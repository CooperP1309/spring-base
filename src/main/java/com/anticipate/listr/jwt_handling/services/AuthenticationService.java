package com.anticipate.listr.jwt_handling.services;

/* ===== local libs ===== */
import com.anticipate.listr.jwt_handling.dtos.LoginUserDto;
import com.anticipate.listr.jwt_handling.dtos.RegisterUserDto;
import com.anticipate.listr.jwt_handling.entities.Role;
import com.anticipate.listr.jwt_handling.entities.User;
import com.anticipate.listr.jwt_handling.repositories.UserRepository;
import com.anticipate.listr.jwt_handling.exceptions.ExpiredVerificationException;
import com.anticipate.listr.jwt_handling.exceptions.InvalidVerificationException;

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
import java.util.Date;
import java.time.LocalDate;

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

    public void verifyEmailSecret(String verificationSecret)
    {
        Optional<User> userOpt = userRepository.findByEmailVerificationSecret(verificationSecret);

        if (userOpt.isEmpty())
        {
            throw new InvalidVerificationException();
        }

        User user = userOpt.get();

        // ensure the user isn't already verified pre expiration check (clicking old links = bad)
        if (user.getEmailVerified())
        {
            return;
        }

        // reject if verification link is more than 1 day old
        if (isExpired(user.getCreatedAt()))
        {
            throw new ExpiredVerificationException(user);
        }

        user.setEmailVerified(true);

        // let this fall to the global exception handler (DataAccessException)
        userRepository.save(user);

        return;
    }

    public String setNewEmailSecret(User user)
    {
        String verificationSecret = secretGeneratorService.generateSecureSecret();

        user.setEmailVerificationSecret(verificationSecret);
        user.setResetSecretGeneratedAt(new Date());

        userRepository.save(user);

        return verificationSecret;
    }

    /*  Resolves a password reset secret to its owning user
     *
     *  Reuses the emailVerificationSecret field (as set by
     *  setNewEmailSecret()) rather than a dedicated secret column, but
     *  expiry is tracked via resetSecretGeneratedAt - NOT createdAt.
     *  createdAt is @Column(updatable = false), so a Hibernate UPDATE
     *  silently drops any change to it on an existing row; reusing it
     *  here would make every reset link for an account older than a
     *  day appear expired immediately.
     *
     *  Unlike verifyEmailSecret(), an expired or unknown secret here
     *  is not exceptional - it's just "no valid reset in progress" -
     *  so this returns empty instead of throwing, and the caller must
     *  not delete the account on that basis (an expired reset link is
     *  not grounds to wipe a user, unlike an expired signup).
     */
    public Optional<User> resolvePasswordResetSecret(String secret)
    {
        Optional<User> userOpt = userRepository.findByEmailVerificationSecret(secret);

        if (userOpt.isEmpty())
        {
            log.info("Password reset secret not found: {}", secret);
            return Optional.empty();
        }

        User user = userOpt.get();

        log.info("Password reset secret '{}' matched user '{}', resetSecretGeneratedAt={}",
                secret, user.getEmail(), user.getResetSecretGeneratedAt());

        if (isExpired(user.getResetSecretGeneratedAt()))
        {
            log.info("Password reset secret expired for user '{}': resetSecretGeneratedAt={}, now={}",
                    user.getEmail(), user.getResetSecretGeneratedAt(), java.time.Instant.now());
            return Optional.empty();
        }

        return userOpt;
    }

    /*  Applies a new password and burns the reset secret
     *
     *  Clearing emailVerificationSecret makes the reset link single
     *  use - revisiting it afterwards resolves to no user, the same
     *  as an unknown secret.
     */
    public void resetPassword(User user, String newPassword)
    {
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setEmailVerificationSecret(null);

        userRepository.save(user);
    }

    private boolean isExpired(Date timestamp)
    {
        if (timestamp == null)
        {
            return true;
        }

        return timestamp.toInstant()
                .plus(1, java.time.temporal.ChronoUnit.DAYS)
                .isBefore(java.time.Instant.now());
    }
}