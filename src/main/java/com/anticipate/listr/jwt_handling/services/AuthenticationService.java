package com.anticipate.listr.jwt_handling.services;

import com.anticipate.listr.jwt_handling.dtos.LoginUserDto;
import com.anticipate.listr.jwt_handling.dtos.RegisterUserDto;
import com.anticipate.listr.jwt_handling.entities.User;
import com.anticipate.listr.jwt_handling.repositories.UserRepository;
import com.anticipate.listr.jwt_handling.services.SecretGeneratorService;
import java.util.Optional;
import org.springframework.dao.DataAccessException;
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

    public boolean verifyEmailSecret(String verificationSecret) {

        Optional<User> userOpt = userRepository.findByEmailVerificationSecret(verificationSecret);

        if (userOpt.isEmpty()) {
            return false;
        }

        User user = userOpt.get();
        user.setEmailVerified(true);

        try {
            userRepository.save(user);
        } catch (DataAccessException e) {
            // error marking user as verified
            return false;
        }

        return true;
    }


    // TEST ENDPOINT TO DELETE LATER
    public String generateSecret() {

        // CHECK THAT THE SECRET DOESN'T ALREADY EXIST

        return secretGeneratorService.generateSecureSecret();
    }
}