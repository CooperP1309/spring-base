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
}
