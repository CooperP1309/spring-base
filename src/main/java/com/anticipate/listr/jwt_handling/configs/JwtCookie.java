package com.anticipate.listr.jwt_handling.configs;

import org.springframework.http.ResponseCookie;

import java.time.Duration;

public final class JwtCookie 
{

    public static final String NAME = "jwt";

    private JwtCookie() {}

    public static ResponseCookie create(String token, long maxAgeMillis) 
    {
        return ResponseCookie.from(NAME, token)
                .httpOnly(true)
                .secure(false)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofMillis(maxAgeMillis))
                .build();
    }

    public static ResponseCookie clear() 
    {
        return ResponseCookie.from(NAME, "")
                .httpOnly(true)
                .secure(false)
                .sameSite("Lax")
                .path("/")
                .maxAge(0)
                .build();
    }
}
