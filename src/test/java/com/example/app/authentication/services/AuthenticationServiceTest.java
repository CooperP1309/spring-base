package com.example.app.authentication.services;

import com.example.app.authentication.entities.Role;
import com.example.app.authentication.entities.User;
import com.example.app.authentication.exceptions.ExpiredVerificationException;
import com.example.app.authentication.exceptions.InvalidVerificationException;
import com.example.app.authentication.repositories.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Date;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest
{
    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private SecretGeneratorService secretGeneratorService;

    private AuthenticationService authenticationService;

    @BeforeEach
    void setUp()
    {
        // the constructor always calls findAllByRole(ADMIN) to prune stray
        // admin accounts, regardless of whether admin.email/password are set,
        // so this stub is required just to construct the service under test
        when(userRepository.findAllByRole(Role.ADMIN)).thenReturn(Collections.emptyList());

        authenticationService = new AuthenticationService(
                userRepository,
                authenticationManager,
                passwordEncoder,
                secretGeneratorService,
                "",
                "");
    }

    @Test
    void verifyEmailSecret_throwsInvalidVerificationException_whenSecretDoesNotMatchAnyUser()
    {
        when(userRepository.findByEmailVerificationSecret("unknown-secret"))
                .thenReturn(Optional.empty());

        assertThrows(
                InvalidVerificationException.class,
                () -> authenticationService.verifyEmailSecret("unknown-secret"));

        verify(userRepository, never()).save(any());
    }

    @Test
    void verifyEmailSecret_throwsExpiredVerificationException_whenLinkIsOlderThanOneDay()
    {
        User user = new User().setEmail("someone@example.com");
        ReflectionTestUtils.setField(user, "createdAt", Date.from(Instant.now().minus(2, ChronoUnit.DAYS)));

        when(userRepository.findByEmailVerificationSecret("expired-secret"))
                .thenReturn(Optional.of(user));

        ExpiredVerificationException thrown = assertThrows(
                ExpiredVerificationException.class,
                () -> authenticationService.verifyEmailSecret("expired-secret"));

        assertEquals(user, thrown.getUser());
        assertFalse(user.getEmailVerified());
        verify(userRepository, never()).save(any());
    }

    @Test
    void verifyEmailSecret_verifiesUser_whenSecretIsValidAndUnexpired()
    {
        User user = new User().setEmail("someone@example.com");
        ReflectionTestUtils.setField(user, "createdAt", Date.from(Instant.now()));

        when(userRepository.findByEmailVerificationSecret("good-secret"))
                .thenReturn(Optional.of(user));

        authenticationService.verifyEmailSecret("good-secret");

        assertTrue(user.getEmailVerified());
        verify(userRepository).save(user);
    }

    @Test
    void verifyEmailSecret_clearsSecret_whenUserIsVerified()
    {
        User user = new User()
                .setEmail("someone@example.com")
                .setEmailVerificationSecret("good-secret");
        ReflectionTestUtils.setField(user, "createdAt", Date.from(Instant.now()));

        when(userRepository.findByEmailVerificationSecret("good-secret"))
                .thenReturn(Optional.of(user));

        authenticationService.verifyEmailSecret("good-secret");

        // the link must be single use - the saved user can no longer be found by this secret
        assertNull(user.getEmailVerificationSecret());
        verify(userRepository).save(user);
    }

    @Test
    void verifyEmailSecret_propagatesDataAccessException_whenSaveFails()
    {
        User user = new User().setEmail("someone@example.com");
        ReflectionTestUtils.setField(user, "createdAt", Date.from(Instant.now()));

        when(userRepository.findByEmailVerificationSecret("good-secret"))
                .thenReturn(Optional.of(user));
        when(userRepository.save(user))
                .thenThrow(new DataIntegrityViolationException("simulated db failure"));

        assertThrows(
                DataAccessException.class,
                () -> authenticationService.verifyEmailSecret("good-secret"));
    }

    @Test
    void setEmailAsNotVerified_disablesUserAndClearsSecret()
    {
        User user = new User()
                .setEmail("someone@example.com")
                .setRole(Role.USER)
                .setEmailVerified(true)
                .setEmailVerificationSecret("outstanding-secret");

        when(userRepository.findByEmail("someone@example.com")).thenReturn(Optional.of(user));

        authenticationService.setEmailAsNotVerified("someone@example.com");

        assertFalse(user.getEmailVerified());
        // a leftover secret must not survive the disable, or it could be replayed on /auth/verify
        assertNull(user.getEmailVerificationSecret());
        verify(userRepository).save(user);
    }

    @Test
    void setEmailAsNotVerified_leavesAdminUntouched()
    {
        User admin = new User()
                .setEmail("admin@example.com")
                .setRole(Role.ADMIN)
                .setEmailVerified(true)
                .setEmailVerificationSecret("admin-secret");

        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));

        authenticationService.setEmailAsNotVerified("admin@example.com");

        assertTrue(admin.getEmailVerified());
        assertEquals("admin-secret", admin.getEmailVerificationSecret());
        verify(userRepository, never()).save(any());
    }

    @Test
    void setEmailAsVerified_enablesUserAndClearsSecret()
    {
        User user = new User()
                .setEmail("someone@example.com")
                .setRole(Role.USER)
                .setEmailVerified(false)
                .setEmailVerificationSecret("registration-secret");

        when(userRepository.findByEmail("someone@example.com")).thenReturn(Optional.of(user));

        authenticationService.setEmailAsVerified("someone@example.com");

        assertTrue(user.getEmailVerified());
        assertNull(user.getEmailVerificationSecret());
        verify(userRepository).save(user);
    }

    @Test
    void resolvePasswordResetSecret_returnsUser_whenResetIsRecentOnOldAccount()
    {
        // createdAt is long past but the reset was just requested - expiry must
        // follow resetSecretGeneratedAt, not createdAt
        User user = new User()
                .setEmail("someone@example.com")
                .setEmailVerificationSecret("reset-secret")
                .setResetSecretGeneratedAt(Date.from(Instant.now()));
        ReflectionTestUtils.setField(user, "createdAt", Date.from(Instant.now().minus(30, ChronoUnit.DAYS)));

        when(userRepository.findByEmailVerificationSecret("reset-secret"))
                .thenReturn(Optional.of(user));

        assertEquals(Optional.of(user), authenticationService.resolvePasswordResetSecret("reset-secret"));
    }

    @Test
    void resolvePasswordResetSecret_returnsEmpty_whenResetIsOlderThanOneDay()
    {
        User user = new User()
                .setEmail("someone@example.com")
                .setEmailVerificationSecret("reset-secret")
                .setResetSecretGeneratedAt(Date.from(Instant.now().minus(2, ChronoUnit.DAYS)));

        when(userRepository.findByEmailVerificationSecret("reset-secret"))
                .thenReturn(Optional.of(user));

        assertTrue(authenticationService.resolvePasswordResetSecret("reset-secret").isEmpty());
    }

    @Test
    void resetPassword_encodesPasswordAndClearsSecret()
    {
        User user = new User()
                .setEmail("someone@example.com")
                .setPassword("old-hash")
                .setEmailVerificationSecret("reset-secret");

        when(passwordEncoder.encode("NewPassword123")).thenReturn("new-hash");

        authenticationService.resetPassword(user, "NewPassword123");

        assertEquals("new-hash", user.getPassword());
        // the reset link must be single use - the saved user can no longer be found by this secret
        assertNull(user.getEmailVerificationSecret());
        verify(userRepository).save(user);
    }
}
