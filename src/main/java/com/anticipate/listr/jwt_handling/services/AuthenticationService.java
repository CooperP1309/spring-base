package com.anticipate.listr.jwt_handling.services;

import com.anticipate.listr.jwt_handling.dtos.LoginUserDto;
import com.anticipate.listr.jwt_handling.dtos.RegisterUserDto;
import com.anticipate.listr.jwt_handling.entities.Role;
import com.anticipate.listr.jwt_handling.entities.User;
import com.anticipate.listr.jwt_handling.repositories.UserRepository;
import com.anticipate.listr.jwt_handling.services.SecretGeneratorService;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthenticationService 
{
    private final UserRepository userRepository;
    
    private final PasswordEncoder passwordEncoder;
    
    private final AuthenticationManager authenticationManager;

    private final SecretGeneratorService secretGeneratorService;

    public AuthenticationService(UserRepository userRepository,
                                    AuthenticationManager authenticationManager,
                                    PasswordEncoder passwordEncoder,
                                    SecretGeneratorService secretGeneratorService,
                                    @Value("${admin.email:}") String adminEmail,
                                    @Value("${admin.password:}") String adminPassword)
    {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.secretGeneratorService = secretGeneratorService;

        // Ensure admin exists in the database
        if (!adminEmail.isBlank() && !adminPassword.isBlank()) {
            
            Optional<User> adminOpt = userRepository.findByEmail(adminEmail);
            if (adminOpt.isEmpty()) {
                User admin = new User()
                        .setFullName("Admin")
                        .setEmail(adminEmail)
                        .setPassword(passwordEncoder.encode(adminPassword))
                        .setEmailVerified(true)
                        .setRole(Role.ADMIN);
                userRepository.save(admin);
            }
            System.out.println("\n[AuthenticationService] adminEmail: " + adminEmail + "\n");
        }
    }

    public User signup(RegisterUserDto input) 
    {    
        String verificationSecret = secretGeneratorService.generateSecureSecret();
        
        User user = new User()
                .setFullName(input.getFullName())
                .setEmail(input.getEmail())
                .setPassword(passwordEncoder.encode(input.getPassword()))
                .setEmailVerified(false)
                .setEmailVerificationSecret(verificationSecret)
                .setRole(Role.USER);

        return userRepository.save(user);
    }

    public User setEmailAsVerified(String verifiedEmail) 
    {    
        User verifiedUser = userRepository.findByEmail(verifiedEmail).orElseThrow();

        verifiedUser.setEmailVerified(true);

        return userRepository.save(verifiedUser);
    }

    public User setEmailAsNotVerified(String verifiedEmail)
    {    
        User verifiedUser = userRepository.findByEmail(verifiedEmail).orElseThrow();

        if (verifiedUser.getRole() == Role.ADMIN)
        {
            return verifiedUser;
        }

        verifiedUser.setEmailVerified(false);

        return userRepository.save(verifiedUser);
    }

    public User authenticate(LoginUserDto input)
    {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        input.getEmail(),
                        input.getPassword()));

        return userRepository.findByEmail(input.getEmail())
                .orElseThrow();
    }

    public boolean verifyEmailSecret(String verificationSecret)
    {
        Optional<User> userOpt = userRepository.findByEmailVerificationSecret(verificationSecret);

        if (userOpt.isEmpty()) {
            return false;
        }

        User user = userOpt.get();
        user.setEmailVerified(true);

        try {
            userRepository.save(user);
        } catch (DataAccessException e) {
            return false;
        }

        return true;
    }
}