package com.anticipate.listr.jwt_handling.services;

/* ===== local libs ===== */
import com.anticipate.listr.jwt_handling.dtos.LoginUserDto;
import com.anticipate.listr.jwt_handling.dtos.RegisterUserDto;
import com.anticipate.listr.jwt_handling.entities.Role;
import com.anticipate.listr.jwt_handling.entities.User;
import com.anticipate.listr.jwt_handling.repositories.UserRepository;

/* ===== spring libs ===== */
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/* ===== java libs ===== */
import lombok.extern.slf4j.Slf4j;
import java.util.Optional;

@Service
@Slf4j
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
        if (!adminEmail.isBlank() && !adminPassword.isBlank()) 
        {
            Optional<User> adminOpt = userRepository.findByEmail(adminEmail);
            if (adminOpt.isEmpty()) 
            {
                User admin = new User()
                        .setFullName("Admin")
                        .setEmail(adminEmail)
                        .setPassword(passwordEncoder.encode(adminPassword))
                        .setEmailVerified(true)
                        .setRole(Role.ADMIN);
                userRepository.save(admin);
            }
        }

        // ensure there's no other admin user in the database
        // start by getting all users with the admin role
        Iterable<User> adminUsers = userRepository.findAllByRole(Role.ADMIN);
        for (User adminUser : adminUsers)
        {
            // if the admin user is not the one we just created, delete it
            if (!adminUser.getEmail().equals(adminEmail))
            {
                userRepository.delete(adminUser);
                log.info("Deleted existing admin user with email: {}", adminUser.getEmail());
            }
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
            log.error("Error saving user with verified email: " + e.getMessage());
            return false;
        }

        return true;
    }
}