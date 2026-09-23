package com.anticipate.listr.jwt_handling.configs;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/*  Carries a CSRF token for requests made before any JWT exists (login,
 *  registration, forgot/reset password). Once a JWT is issued its own
 *  embedded csrf claim (see JwtService) takes over as the expected token
 *  for that browser instead - this cookie is only the pre-auth fallback.
 */
@Component
public class AnonymousCsrfCookie
{
    public static final String NAME = "csrf";

    private static final Duration MAX_AGE = Duration.ofHours(1);

    private final boolean secure;

    public AnonymousCsrfCookie(@Value("${security.jwt.cookie.secure}") boolean secure)
    {
        this.secure = secure;
    }

    public ResponseCookie create(String token)
    {
        return ResponseCookie.from(NAME, token)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/")
                .maxAge(MAX_AGE)
                .build();
    }
}
