package com.anticipate.listr.jwt_handling.controllers;

import com.anticipate.listr.jwt_handling.entities.User;
import com.anticipate.listr.jwt_handling.dtos.LoginUserDto;
import com.anticipate.listr.jwt_handling.dtos.RegisterUserDto;
import com.anticipate.listr.jwt_handling.dtos.RegisterEmailDto;
import com.anticipate.listr.jwt_handling.responses.LoginResponse;
import com.anticipate.listr.jwt_handling.services.AuthenticationService;
import com.anticipate.listr.jwt_handling.services.JwtService;
import com.anticipate.listr.jwt_handling.repositories.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import java.util.Optional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.stereotype.Controller;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.apache.commons.validator.routines.EmailValidator;

@RequestMapping("/auth")
@Controller
public class AuthenticationController {

    @Value("${smtp.sender.email}")
    private String senderEmail;

    @Value("${smtp.receiver.email}")
    private String receiverEmail;

    private JavaMailSender mailSender;

    private EmailValidator emailValidator;

    private final UserRepository userRepository;

    private final JwtService jwtService;
    
    private final AuthenticationService authenticationService;

    public AuthenticationController(JwtService jwtService, AuthenticationService authenticationService, JavaMailSender mailSender, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.authenticationService = authenticationService;
        this.mailSender = mailSender;
        this.emailValidator = EmailValidator.getInstance();
        this.userRepository = userRepository;
    }

    @PostMapping("/signup")
    public ResponseEntity<User> register(@RequestBody RegisterUserDto registerUserDto) {
        User registeredUser = authenticationService.signup(registerUserDto);

        return ResponseEntity.ok(registeredUser);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> authenticate(@RequestBody LoginUserDto loginUserDto) {
        User authenticatedUser = authenticationService.authenticate(loginUserDto);

        String jwtToken = jwtService.generateToken(authenticatedUser);

        LoginResponse loginResponse = new LoginResponse().setToken(jwtToken).setExpiresIn(jwtService.getExpirationTime());

        return ResponseEntity.ok(loginResponse);
    }

    // TEST ENDPOINTS
    @ResponseBody
    @PostMapping("/test-email")
    public String test_email(@RequestBody RegisterEmailDto registerEmailDto) {
       
       /* 
        String body = "This is a test email!!!!";
        String subject = "TEST";

        System.out.println("Registered email.");
        

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(this.senderEmail);
        message.setTo(this.receiverEmail);
        message.setSubject(subject);
        message.setText(body);
        
        mailSender.send(message);
        */

        EmailValidator validator = EmailValidator.getInstance();

        boolean emailValid = validator.isValid(registerEmailDto.getEmail());

        return "The following email: '" + registerEmailDto.getEmail() + "' has validity: " + emailValid;
    }

    @PostMapping("/test-emailVerification")
    @ResponseBody
    public String test_emailVerification(@RequestBody LoginUserDto loginUserDto) {

        //User newUser = userRepository.findByEmail(loginUserDto.getEmail());


        User newUser = userRepository.findByEmail(loginUserDto.getEmail()).orElseThrow();



        System.out.println("Email '" + newUser.getEmail() + "' verified = " + newUser.getEmailVerified());

        return "Email '" + newUser.getEmail() + "' verified = " + newUser.getEmailVerified();
    }    

    @PostMapping("/set-emailVerification")
    @ResponseBody
    public String set_emailVerification(@RequestBody RegisterEmailDto registerEmailDto) {

        authenticationService.setEmailAsVerified(registerEmailDto.getEmail());

        return "Email '" + registerEmailDto.getEmail() + "' verified";
    }

    @PostMapping("/unset-emailVerification")
    @ResponseBody
    public String unset_emailVerification(@RequestBody RegisterEmailDto registerEmailDto) {

        authenticationService.setEmailAsNotVerified(registerEmailDto.getEmail());

        return "Email '" + registerEmailDto.getEmail() + "' verified";
    }

    @PostMapping("/generate-secret")
    @ResponseBody
    public String generate_secret(@RequestBody RegisterEmailDto registerEmailDto) {

        return authenticationService.generateSecret();
    }

    @PostMapping("/test-secret")
    @ResponseBody
    public String test_email_secret(@RequestBody RegisterEmailDto registerEmailDto) {

        User newUser = userRepository.findByEmail(registerEmailDto.getEmail()).orElseThrow();

        String secret = newUser.getEmailVerificationSecret();
        String userEmail = newUser.getEmail();

        System.out.println("User '" + userEmail + "' has secret: '" + secret + "'\n");

        return "secret for '" + newUser.getEmail() + "' = " + secret;
    }

    @GetMapping("/find-by-secret/{secret}")
    @ResponseBody
    public ResponseEntity<String> find_by_secret(@PathVariable String secret) {

        System.out.println("Searching for user with secret: '" + secret + "'\n");

        boolean verified = authenticationService.verifyEmailSecret(secret);

        if (!verified) {
            return ResponseEntity
                    .status(HttpStatus.GONE)
                    .body("This verification link is invalid or has expired.");
        }

        return ResponseEntity.ok("Email has been verified.");
    }

    // PROTOTYPE ENDPOINTS
    @GetMapping("/login-page")
    public String login_page(Model model) {

        model.addAttribute("user", new LoginUserDto());

        return "login-page";
    }
    
    @GetMapping("/register-page")
    public String register_page(Model model) {

        model.addAttribute("user", new RegisterUserDto());

        return "register-page";
    }

    @PostMapping("/test-register")
    /*  
    *   Triggers a prototype pipeline
    *   
    *   This function is called when the above prototype form is
    *   submitted. The passed email is first validated.
    *   Should validation pass, the registration pipline carries out.
    *   A new user is registered and the verification email pipeline
    *   is initiated.
    */
    public String test_register(@ModelAttribute("user") RegisterUserDto newUser) {

        // determine email format valid before continuing pipeline
        if (!this.emailValidator.isValid(newUser.getEmail())) {
            newUser.setEmailValid(false);

            return "register-page";
        }

        newUser.setEmailValid(true);

        // add user to db
        User registeredUser = authenticationService.signup(newUser);

        System.out.println("\nRegistered user: " + registeredUser.getEmail()
                + "\nVerification Link: 'http://localhost:8005/auth/verify/" + registeredUser.getEmailVerificationSecret() + "'\n");

        return "register-page";
    }

    @GetMapping("/verify/{secret}")
    @ResponseBody
    public ResponseEntity<String> verifySecret(@PathVariable String secret) {

        boolean verified = authenticationService.verifyEmailSecret(secret);

        if (!verified) {
            return ResponseEntity
                    .status(HttpStatus.GONE)
                    .body("This verification link is invalid or has expired.");
        }

        return ResponseEntity.ok("Email has been verified.");
    }
}