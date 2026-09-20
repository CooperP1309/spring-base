package com.anticipate.listr.jwt_handling.controllers;

import com.anticipate.listr.jwt_handling.dtos.ForgotPasswordDto;
import com.anticipate.listr.jwt_handling.entities.User;
import com.anticipate.listr.jwt_handling.repositories.UserRepository;
import com.anticipate.listr.jwt_handling.services.AuthenticationService;
import com.anticipate.listr.jwt_handling.services.JwtService;
import com.anticipate.listr.jwt_handling.services.SMTPService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticationControllerTest
{
    @Mock
    private JwtService jwtService;

    @Mock
    private AuthenticationService authenticationService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SMTPService smtpService;

    private AuthenticationController authenticationController;

    @BeforeEach
    void setUp()
    {
        authenticationController = new AuthenticationController(
                jwtService,
                authenticationService,
                userRepository,
                smtpService);
    }

    @Test
    void forgotPassword_returnsGenericMessage_whenEmailIsNotValidFormat()
    {
        ForgotPasswordDto input = new ForgotPasswordDto().setEmail("not-an-email");
        BindingResult bindingResult = new BeanPropertyBindingResult(input, "user");

        when(userRepository.findByEmail("not-an-email")).thenReturn(Optional.empty());

        String view = authenticationController.forgotPassword(input, bindingResult);

        assertEquals("forgot-password-page", view);
        assertEquals("Password Reset link sent.", bindingResult.getGlobalError().getDefaultMessage());
        verify(authenticationService, never()).setNewEmailSecret(any());
    }

    @Test
    void forgotPassword_returnsGenericMessage_whenEmailIsNotRegistered()
    {
        ForgotPasswordDto input = new ForgotPasswordDto().setEmail("unknown@example.com");
        BindingResult bindingResult = new BeanPropertyBindingResult(input, "user");

        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        String view = authenticationController.forgotPassword(input, bindingResult);

        assertEquals("forgot-password-page", view);
        assertEquals("Password Reset link sent.", bindingResult.getGlobalError().getDefaultMessage());
        verify(authenticationService, never()).setNewEmailSecret(any());
    }

    @Test
    void forgotPassword_returnsGenericMessage_whenEmailIsUnverified()
    {
        ForgotPasswordDto input = new ForgotPasswordDto().setEmail("unverified@example.com");
        BindingResult bindingResult = new BeanPropertyBindingResult(input, "user");

        User unverifiedUser = new User()
                .setEmail("unverified@example.com")
                .setEmailVerified(false);

        when(userRepository.findByEmail("unverified@example.com")).thenReturn(Optional.of(unverifiedUser));

        String view = authenticationController.forgotPassword(input, bindingResult);

        assertEquals("forgot-password-page", view);
        assertEquals("Password Reset link sent.", bindingResult.getGlobalError().getDefaultMessage());
        verify(authenticationService, never()).setNewEmailSecret(any());
    }

    @Test
    void forgotPassword_returnsFailureMessage_whenSmtpServiceFailsToSendResetLink()
    {
        ForgotPasswordDto input = new ForgotPasswordDto().setEmail("verified@example.com");
        BindingResult bindingResult = new BeanPropertyBindingResult(input, "user");

        User verifiedUser = new User()
                .setEmail("verified@example.com")
                .setEmailVerified(true);

        when(userRepository.findByEmail("verified@example.com")).thenReturn(Optional.of(verifiedUser));
        when(authenticationService.setNewEmailSecret(verifiedUser)).thenReturn("new-secret");
        when(smtpService.sendPasswordResetLink("new-secret", "verified@example.com")).thenReturn("Failure");

        String view = authenticationController.forgotPassword(input, bindingResult);

        assertEquals("forgot-password-page", view);
        assertEquals("Password Reset link failed to send. Please try again later.",
                bindingResult.getGlobalError().getDefaultMessage());
    }
}
