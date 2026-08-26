package com.anticipate.listr.jwt_handling.controllers;

import com.anticipate.listr.jwt_handling.entities.User;
import com.anticipate.listr.jwt_handling.dtos.LoginUserDto;
import com.anticipate.listr.jwt_handling.dtos.RegisterUserDto;
import com.anticipate.listr.jwt_handling.dtos.RegisterEmailDto;
import com.anticipate.listr.jwt_handling.responses.LoginResponse;
import com.anticipate.listr.jwt_handling.services.AuthenticationService;
import com.anticipate.listr.jwt_handling.services.JwtService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.stereotype.Controller;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

@RequestMapping("/auth")
@Controller
public class AuthenticationController {

    @Value("${smtp.sender.email}")
    private String senderEmail;

    @Value("${smtp.receiver.email}")
    private String receiverEmail;

    private JavaMailSender mailSender;

    private final JwtService jwtService;
    
    private final AuthenticationService authenticationService;

    public AuthenticationController(JwtService jwtService, AuthenticationService authenticationService, JavaMailSender mailSender) {
        this.jwtService = jwtService;
        this.authenticationService = authenticationService;
        this.mailSender = mailSender;
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

        return "You passed the following email: '" + registerEmailDto.getEmail() + "'";
    }

    @GetMapping("/register-page")
    public String register_page(Model model) {

        model.addAttribute("user", new LoginUserDto());

        return "register-page";
    }

    @PostMapping("/test-register")
    public String test_register(@ModelAttribute("user") LoginUserDto newUser) {

        System.out.println("\nRegistered email:" + newUser.getEmail() + "\nI shouldn't know this: " + newUser.getPassword() + "\n");

        newUser.setEmailValid(true);

        return "register-page";
    }

}