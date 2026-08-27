package com.anticipate.listr.jwt_handling.services;

import com.anticipate.listr.jwt_handling.dtos.LoginUserDto;
import com.anticipate.listr.jwt_handling.dtos.RegisterUserDto;
import com.anticipate.listr.jwt_handling.entities.User;
import com.anticipate.listr.jwt_handling.repositories.UserRepository;
import com.anticipate.listr.jwt_handling.services.SecretGeneratorService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthenticationService {
    private final UserRepository userRepository;
    
    private final PasswordEncoder passwordEncoder;
    
    private final AuthenticationManager authenticationManager;

    private final SecretGeneratorService secretGeneratorService;

    public AuthenticationService(
        UserRepository userRepository,
        AuthenticationManager authenticationManager,
        PasswordEncoder passwordEncoder,
        SecretGeneratorService secretGeneratorService
    ) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.secretGeneratorService = secretGeneratorService;
    }

    public User signup(RegisterUserDto input) {
        
        String verificationSecret = secretGeneratorService.generateSecureSecret();
        
        User user = new User()
                .setFullName(input.getFullName())
                .setEmail(input.getEmail())
                .setPassword(passwordEncoder.encode(input.getPassword()))
                .setEmailVerified(false)
                .setEmailVerificationSecret(verificationSecret);

        return userRepository.save(user);
    }

    public User setEmailAsVerified(String verifiedEmail) {
        
        User verifiedUser = userRepository.findByEmail(verifiedEmail).orElseThrow();

        verifiedUser.setEmailVerified(true);

        return userRepository.save(verifiedUser);
    }

    public User setEmailAsNotVerified(String verifiedEmail) {
        
        User verifiedUser = userRepository.findByEmail(verifiedEmail).orElseThrow();

        verifiedUser.setEmailVerified(false);

        return userRepository.save(verifiedUser);
    }

    public User authenticate(LoginUserDto input) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        input.getEmail(),
                        input.getPassword()
                )
        );

        return userRepository.findByEmail(input.getEmail())
                .orElseThrow();
    }


    // TEST ENDPOINT TO DELETE LATER
    public String generateSecret() {

        // CHECK THAT THE SECRET DOESN'T ALREADY EXIST

        return secretGeneratorService.generateSecureSecret();
    }
}