package com.anticipate.listr.jwt_handling.configs;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class JwtCookie
{
    public static final String NAME = "jwt";

    private final boolean secure;

    public JwtCookie(@Value("${security.jwt.cookie.secure}") boolean secure)
    {
        this.secure = secure;
    }

    public ResponseCookie create(String token, long maxAgeMillis)
    {
        return ResponseCookie.from(NAME, token)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofMillis(maxAgeMillis))
                .build();
    }

    public ResponseCookie clear()
    {
        return ResponseCookie.from(NAME, "")
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/")
                .maxAge(0)
                .build();
    }
}
