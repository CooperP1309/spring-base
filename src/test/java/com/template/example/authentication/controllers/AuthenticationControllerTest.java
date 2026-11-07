package com.template.example.authentication.controllers;

import com.template.example.authentication.dtos.ForgotPasswordDto;
import com.template.example.authentication.dtos.LoginUserDto;
import com.template.example.authentication.dtos.RegisterUserDto;
import com.template.example.authentication.dtos.ResetPasswordDto;
import com.template.example.authentication.configs.JwtCookie;
import com.template.example.authentication.entities.User;
import com.template.example.authentication.repositories.UserRepository;
import com.template.example.authentication.services.AuthenticationService;
import com.template.example.authentication.services.JwtService;
import com.template.example.authentication.services.SMTPService;
import com.template.example.authentication.services.TurnstileService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
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

    @Mock
    private JwtCookie jwtCookie;

    @Mock
    private TurnstileService turnstileService;

    private AuthenticationController authenticationController;

    @BeforeEach
    void setUp()
    {
        authenticationController = new AuthenticationController(
                jwtService,
                authenticationService,
                userRepository,
                smtpService,
                jwtCookie,
                turnstileService);
    }

    @Test
    void forgotPassword_returnsGenericMessage_whenEmailIsNotValidFormat()
    {
        ForgotPasswordDto input = new ForgotPasswordDto().setEmail("not-an-email");
        BindingResult bindingResult = new BeanPropertyBindingResult(input, "user");

        when(userRepository.findByEmail("not-an-email")).thenReturn(Optional.empty());

        when(turnstileService.verify("test")).thenReturn(true);

        String view = authenticationController.forgotPassword(input, bindingResult, "test");

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

        when(turnstileService.verify("test")).thenReturn(true);

        String view = authenticationController.forgotPassword(input, bindingResult, "test");

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

        when(turnstileService.verify("test")).thenReturn(true);

        String view = authenticationController.forgotPassword(input, bindingResult, "test");

        assertEquals("forgot-password-page", view);
        assertEquals("Password Reset link sent.", bindingResult.getGlobalError().getDefaultMessage());
        verify(authenticationService, never()).setNewEmailSecret(any());
    }

    @Test
    void resetPassword_showsInvalidSecret_whenSecretDoesNotResolve()
    {
        ResetPasswordDto input = new ResetPasswordDto().setPassword("NewPassword123");
        BindingResult bindingResult = new BeanPropertyBindingResult(input, "user");
        Model model = new ExtendedModelMap();

        when(authenticationService.resolvePasswordResetSecret("bad-secret")).thenReturn(Optional.empty());

        String view = authenticationController.resetPassword("bad-secret", input, bindingResult, model);

        assertEquals("reset-password-page", view);
        assertEquals(true, model.getAttribute("secretInvalid"));
        assertEquals(false, model.getAttribute("resetSuccess"));
        verify(authenticationService, never()).resetPassword(any(), any());
    }

    @Test
    void resetPassword_redisplaysForm_whenValidationErrors()
    {
        ResetPasswordDto input = new ResetPasswordDto().setPassword("short");
        BindingResult bindingResult = new BeanPropertyBindingResult(input, "user");
        bindingResult.rejectValue("password", "Size", "Password must be at least 12 characters long");
        Model model = new ExtendedModelMap();

        User user = new User().setEmail("verified@example.com");

        when(authenticationService.resolvePasswordResetSecret("good-secret")).thenReturn(Optional.of(user));

        String view = authenticationController.resetPassword("good-secret", input, bindingResult, model);

        assertEquals("reset-password-page", view);
        assertEquals(false, model.getAttribute("secretInvalid"));
        assertEquals(false, model.getAttribute("resetSuccess"));
        verify(authenticationService, never()).resetPassword(any(), any());
    }

    @Test
    void resetPassword_completesReset_whenSecretValidAndNoErrors()
    {
        ResetPasswordDto input = new ResetPasswordDto().setPassword("NewPassword123");
        BindingResult bindingResult = new BeanPropertyBindingResult(input, "user");
        Model model = new ExtendedModelMap();

        User user = new User().setEmail("verified@example.com");

        when(authenticationService.resolvePasswordResetSecret("good-secret")).thenReturn(Optional.of(user));

        String view = authenticationController.resetPassword("good-secret", input, bindingResult, model);

        assertEquals("reset-password-page", view);
        assertEquals(false, model.getAttribute("secretInvalid"));
        assertEquals(true, model.getAttribute("resetSuccess"));
        verify(authenticationService).resetPassword(user, "NewPassword123");
    }

    @Test
    void login_rejected_whenCaptchaFails()
    {
        LoginUserDto input = new LoginUserDto().setEmail("user@example.com").setPassword("Password12345");
        BindingResult bindingResult = new BeanPropertyBindingResult(input, "user");

        when(turnstileService.verify("bad-token")).thenReturn(false);

        String view = authenticationController.authenticate(input, bindingResult, new ExtendedModelMap(), null, "bad-token");

        assertEquals("login-page", view);
        assertEquals("captcha.failed", bindingResult.getGlobalError().getCode());
        verify(authenticationService, never()).authenticate(any());
    }

    @Test
    void register_rejected_whenCaptchaFails()
    {
        RegisterUserDto input = new RegisterUserDto()
                .setFullName("Jane Doe")
                .setEmail("new@example.com")
                .setPassword("Password12345");
        BindingResult bindingResult = new BeanPropertyBindingResult(input, "user");
        Model model = new ExtendedModelMap();

        when(turnstileService.verify(null)).thenReturn(false);

        String view = authenticationController.register(input, bindingResult, model, null);

        assertEquals("register-page", view);
        assertEquals("captcha.failed", bindingResult.getGlobalError().getCode());
        assertEquals(null, model.getAttribute("registrationSuccess"));
        verify(authenticationService, never()).signup(any());
    }
}
