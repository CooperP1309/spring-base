package com.anticipate.listr.jwt_handling.services;

import com.anticipate.listr.jwt_handling.entities.User;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtServiceTest
{
    @Mock
    private SecretGeneratorService secretGeneratorService;

    private JwtService jwtService;

    @BeforeEach
    void setUp()
    {
        jwtService = new JwtService(secretGeneratorService);

        ReflectionTestUtils.setField(jwtService, "secretKey",
                Base64.getEncoder().encodeToString("test-signing-key-test-signing-key".getBytes()));
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", 3_600_000L);
    }

    @Test
    void generateToken_embedsCsrfClaim_extractableFromTheSameToken()
    {
        User user = new User().setEmail("someone@example.com");

        when(secretGeneratorService.generateSecureSecret()).thenReturn("generated-csrf-secret");

        String token = jwtService.generateToken(user);

        assertEquals("generated-csrf-secret", jwtService.extractCsrfToken(token));
        assertEquals("someone@example.com", jwtService.extractUsername(token));
    }

    @Test
    void generateToken_givesEachLoginADistinctCsrfClaim()
    {
        User user = new User().setEmail("someone@example.com");

        when(secretGeneratorService.generateSecureSecret())
                .thenReturn("first-login-secret")
                .thenReturn("second-login-secret");

        String firstToken = jwtService.generateToken(user);
        String secondToken = jwtService.generateToken(user);

        String firstCsrf = jwtService.extractCsrfToken(firstToken);
        String secondCsrf = jwtService.extractCsrfToken(secondToken);

        assertNotNull(firstCsrf);
        assertNotNull(secondCsrf);
        assertNotEquals(firstCsrf, secondCsrf);
    }
}
