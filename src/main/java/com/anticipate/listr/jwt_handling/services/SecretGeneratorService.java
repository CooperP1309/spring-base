package com.anticipate.listr.jwt_handling.services;

import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Service;

@Service
public class SecretGeneratorService {

    // 32 bytes provides a secure 256-bit key
    private static final int SECRET_BYTE_LENGTH = 32; 

    public String generateSecureSecret() {
        SecureRandom secureRandom = new SecureRandom();
        byte[] randomBytes = new byte[SECRET_BYTE_LENGTH];
        
        // Populate the array with cryptographically secure random bytes
        secureRandom.nextBytes(randomBytes);
        
        // Encode to a safe, readable String representation
        return Base64.getEncoder().encodeToString(randomBytes);
    }
}
