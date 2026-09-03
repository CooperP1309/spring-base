package com.anticipate.listr.jwt_handling.controllers;

/* ===== local libs ===== */
import com.anticipate.listr.jwt_handling.entities.User;
import com.anticipate.listr.jwt_handling.dtos.LoginUserDto;
import com.anticipate.listr.jwt_handling.dtos.RegisterUserDto;
import com.anticipate.listr.jwt_handling.dtos.SetAccountEnabledDto;
import com.anticipate.listr.jwt_handling.responses.LoginResponse;
import com.anticipate.listr.jwt_handling.services.AuthenticationService;
import com.anticipate.listr.jwt_handling.services.JwtService;
import com.anticipate.listr.jwt_handling.repositories.UserRepository;
import com.anticipate.listr.jwt_handling.services.SMTPService;

/* ===== spring libs ===== */
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import com.anticipate.listr.jwt_handling.configs.JwtCookie;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.stereotype.Controller;
import org.apache.commons.validator.routines.EmailValidator;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.validation.BindingResult;

/* ===== java libs =====*/
import java.util.Optional;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;

@RequestMapping("/auth")
@Controller
@Slf4j
public class AuthenticationController 
{

    private final JwtService jwtService;
    
    private final AuthenticationService authenticationService;

    private EmailValidator emailValidator;

    private final UserRepository userRepository;

    private final SMTPService smtpService;

    public AuthenticationController(JwtService jwtService, 
                                    AuthenticationService authenticationService, 
                                    UserRepository userRepository,
                                    SMTPService smtpService) 
    {
        this.jwtService = jwtService;
        this.authenticationService = authenticationService;
        this.emailValidator = EmailValidator.getInstance();
        this.userRepository = userRepository;
        this.smtpService = smtpService;
    }

    @PostMapping("/login")
    /*  For authorizing user login credentials
     *
     *  This endpoint deserializes JSON and authenticates
     *  the passed login credentials to produce a JWT token.
     */
    public ResponseEntity<LoginResponse> authenticate(@RequestBody LoginUserDto loginUserDto,
                                                        BindingResult bindingResult) 
    {
        if (bindingResult.hasErrors()) 
        {
            log.warn("Login request rejected due to errors: {}", bindingResult.getAllErrors());
            return ResponseEntity.badRequest().build();
        }

        User authenticatedUser = authenticationService.authenticate(loginUserDto);

        String jwtToken = jwtService.generateToken(authenticatedUser);

        LoginResponse loginResponse = new LoginResponse().setToken(jwtToken).setExpiresIn(jwtService.getExpirationTime());

        String cookie = JwtCookie.create(jwtToken, jwtService.getExpirationTime()).toString();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie)
                .body(loginResponse);
    }

    @GetMapping("/login-page")
    /*  Presents a login form
     *
     *  This endpoint responds with a login form.
     *  The forms login button is linked to the above
     *  login endpoint. The form in question uses JS
     *  voodoo to serialize the login credentials to JSON.
     *  This is because apparently you can't build JSON bodies
     *  from html forms, and I wanted to keep the backend as
     *  standard as possible. Extra note, form has a signup
     *  buttom which links to the /register-page endpoint.
     */
    public String loginPage(Model model) 
    {
        model.addAttribute("user", new LoginUserDto());

        return "login-page";
    }
    
    @GetMapping("/register-page")
    /*  Presents the user registration page
     *  
     *  This form allows users to input their full name,
     *  email and password in order to create a new account.
     *  Once these details are submitted, the fields are passed
     *  to /register where the registration pipeline is 
     *  carried out.
     */
    public String registerPage(Model model) 
    {
        model.addAttribute("user", new RegisterUserDto());

        return "register-page";
    }

    @PostMapping("/register")
    /*  
    *   Encapsulates the entire registration process
    *   
    *   This function is responsible for the entire registration
    *   pipeline. A new user goes in (contains email, full name and password).  
    *   Immediately, the validity of the provided email is checked. If valid,
    *   the user is added to the database, with a verified field set to false.
    *   The sending of a verification email is triggered. The email in question
    *   will contain a link that won't allow loggin in with the account 
    *   until the link is opened.
    */
    public String register(@Valid @ModelAttribute("user") RegisterUserDto newUser,
                           BindingResult bindingResult,
                           Model model)
    {
        // validate email format and existence
        if (!bindingResult.hasFieldErrors("email") && 
            !this.emailValidator.isValid(newUser.getEmail()))
        {
            /* My first introduction to bindingResult
             *
             * Why we don't just add a string attribute to the model;
             * - bindingResult is a Spring object that can be read by every downstream
             *   Spring module
             * - It can be used to resolve front end error messages without having to type
             *   them each time.
             *
             * rejectValue() syntax:
             * - field: the name of the field that has an error
             * - errorCode: a code that can be used to resolve a custom defined error
             *   message in "messages.properties"
             * - defaultMessage: a default message to be used if no custom message is found
             */
            bindingResult.rejectValue("email", "email.invalid", "Invalid email provided.");
            log.debug("Invalid email format provided during registration: {}", newUser.getEmail());
        }

        // bind all other errors to the result
        if (bindingResult.hasErrors())
        {
            log.debug("Registration rejected due to errors: {}", bindingResult.getAllErrors());

            return "register-page";
        }

        /*  Why are there two if gates for bindingResult?
         *
         *  The first gate checks against the email validator (a seperate module).
         *  If it's a problem, the error is added to the binding result, but no rejection
         *  of the registration is made yet.
         * 
         *  By the second gate, if there are any other errors at all, (automatically added
         *  by the @Valid annotation (see user entity - password field) and by other means), 
         *  we reject the registration.
         * 
         *  BindingResult sets errors through rejectValue() and reject() and gets errors 
         *  through hasErrors() and getAllErrors(). The result of errors is reflected in the
         *  front end by thymeleaf. (see register-page.html for example)
         */

        // add user to db
        User registeredUser = null;

        try
        {
            registeredUser = authenticationService.signup(newUser);
        }
        catch (DataIntegrityViolationException e)
        {
            log.error("Registration failed for email: {}. Exception: {}", newUser.getEmail(), e.getMessage());
            bindingResult.reject("registration.failed",
                    "Internal error adding user details. Please contact the system administrator.");

            return "register-page";
        }

        // send verificaiton email
        String result = smtpService.sendVerificationLink(registeredUser.getEmailVerificationSecret(),
                                                            registeredUser.getEmail());

        log.info("Verification email status '{}': '{}'", newUser.getEmail(), result);

        if (result.equals("Failure"))
        {
            // ensure to delete user from db so that they can reattempt later
            userRepository.delete(registeredUser);
            bindingResult.reject("verification.email.failed",
                    "We couldn't send your verification email. Please try registering again.");

            return "register-page";
        }

        log.info("User registered successfully: {}", newUser.getEmail());
        model.addAttribute("registrationSuccess", true);

        return "register-page";
    }

    @GetMapping("/verify/{secret}")
    @ResponseBody
    /*  The verification link endpoint
     *  
     *  When a secret is sent to this endpoint, the secret verified as
     *  being linked to an existing user. From there, the users "verified"
     *  field is set to true. This ultimately allows the logging in of that
     *  user from there on out.
     */
    public ResponseEntity<String> verifySecret(@PathVariable String secret)
    {
        boolean verified = authenticationService.verifyEmailSecret(secret);

        if (!verified) {
            return ResponseEntity
                    .status(HttpStatus.GONE)
                    .body("This verification link is invalid or has expired.");
        }

        return ResponseEntity.ok("Email has been verified.");
    }

    @PostMapping("/set-account-enabled")
    @ResponseBody
    /*  Enables or disables a user account
     *
     *  A user account is considered "enabled" once its email is
     *  verified (see User.isEnabled()). This endpoint lets an admin
     *  flip that flag directly from the dashboard, toggling the
     *  target user's ability to log in.
     */
    public ResponseEntity<Void> setAccountEnabled(@RequestBody SetAccountEnabledDto input)
    {
        if (input.isEnabled()) {
            authenticationService.setEmailAsVerified(input.getEmail());
        } else {
            authenticationService.setEmailAsNotVerified(input.getEmail());
        }

        return ResponseEntity.noContent().build();
    }
}