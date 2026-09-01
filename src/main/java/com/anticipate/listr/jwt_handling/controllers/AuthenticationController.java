package com.anticipate.listr.jwt_handling.controllers;

/* ===== local libs ===== */
import com.anticipate.listr.jwt_handling.entities.User;
import com.anticipate.listr.jwt_handling.dtos.LoginUserDto;
import com.anticipate.listr.jwt_handling.dtos.RegisterUserDto;
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

/* ===== java libs =====*/
import java.util.Optional;

@RequestMapping("/auth")
@Controller
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
    public ResponseEntity<LoginResponse> authenticate(@RequestBody LoginUserDto loginUserDto) 
    {
        User authenticatedUser = authenticationService.authenticate(loginUserDto);

        String jwtToken = jwtService.generateToken(authenticatedUser);

        LoginResponse loginResponse = new LoginResponse().setToken(jwtToken).setExpiresIn(jwtService.getExpirationTime());

        System.out.println("Login Response: " + loginResponse.getToken() + " Expires in: " + loginResponse.getExpiresIn());

        // Set the token as an HttpOnly cookie so browser navigations to
        // server-rendered pages (e.g. /home-page) carry it automatically.
        // The token stays in the body too for pure API clients.
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
    public String register(@ModelAttribute("user") RegisterUserDto newUser, Model model) 
    {

        // determine email format valid before continuing pipeline
        if (!this.emailValidator.isValid(newUser.getEmail())) 
        {
            model.addAttribute("emailValid", false);
            return "register-page";
        }

        model.addAttribute("emailValid", true);

        // add user to db
        User registeredUser = authenticationService.signup(newUser);

        // send the user their verification link
        String result = smtpService.sendVerificationLink(registeredUser.getEmailVerificationSecret(), 
                                                            registeredUser.getEmail());

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
}