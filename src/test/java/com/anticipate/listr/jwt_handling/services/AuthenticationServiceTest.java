package com.anticipate.listr.jwt_handling.services;

import com.anticipate.listr.jwt_handling.entities.Role;
import com.anticipate.listr.jwt_handling.entities.User;
import com.anticipate.listr.jwt_handling.exceptions.ExpiredVerificationException;
import com.anticipate.listr.jwt_handling.exceptions.InvalidVerificationException;
import com.anticipate.listr.jwt_handling.repositories.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
    void verifyEmailSecret_verifiesUserAndReturnsTrue_whenSecretIsValidAndUnexpired()
    {
        User user = new User().setEmail("someone@example.com");
        ReflectionTestUtils.setField(user, "createdAt", Date.from(Instant.now()));

        when(userRepository.findByEmailVerificationSecret("good-secret"))
                .thenReturn(Optional.of(user));

        boolean result = authenticationService.verifyEmailSecret("good-secret");

        assertTrue(result);
        assertTrue(user.getEmailVerified());
        verify(userRepository).save(user);
    }

    @Test
    void verifyEmailSecret_returnsFalse_whenSaveFails()
    {
        User user = new User().setEmail("someone@example.com");
        ReflectionTestUtils.setField(user, "createdAt", Date.from(Instant.now()));

        when(userRepository.findByEmailVerificationSecret("good-secret"))
                .thenReturn(Optional.of(user));
        when(userRepository.save(user))
                .thenThrow(new DataIntegrityViolationException("simulated db failure"));

        boolean result = authenticationService.verifyEmailSecret("good-secret");

        assertFalse(result);
    }
}
