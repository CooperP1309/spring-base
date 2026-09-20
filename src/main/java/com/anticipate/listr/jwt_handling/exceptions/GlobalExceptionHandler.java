package com.anticipate.listr.jwt_handling.exceptions;

import com.anticipate.listr.jwt_handling.configs.JwtCookie;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AccountStatusException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.util.NoSuchElementException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler
{
    private final JwtCookie jwtCookie;

    public GlobalExceptionHandler(JwtCookie jwtCookie)
    {
        this.jwtCookie = jwtCookie;
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ProblemDetail handleNotFound(NoSuchElementException exception) 
    {
        ProblemDetail errorDetail = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(404), exception.getMessage());
        errorDetail.setProperty("description", "The requested resource was not found");

        return errorDetail;
    }

    @ExceptionHandler(Exception.class)
    public Object handleSecurityException(Exception exception)
    {
        ProblemDetail errorDetail = null;

        if (exception instanceof UsernameNotFoundException)
        {
            /*  The account behind a still-valid JWT cookie can vanish
             *  (e.g. deleted by an admin) between requests. Rather than
             *  surfacing a raw JSON error to what is otherwise an HTML
             *  app, send the browser back to the landing page - and
             *  clear the now-dangling cookie, otherwise it's resent on
             *  the very next request (including the redirect target
             *  itself) and re-triggers this same exception forever.
             */
            log.warn("Username not found exception: {}", exception.getMessage());

            return ResponseEntity.status(HttpStatusCode.valueOf(302))
                    .location(URI.create("/landing-page"))
                    .header(HttpHeaders.SET_COOKIE, jwtCookie.clear().toString())
                    .build();
        }

        if (exception instanceof BadCredentialsException)
        {
            log.warn("Bad credentials exception: {}", exception.getMessage());

            errorDetail = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(401), exception.getMessage());
            errorDetail.setProperty("description", "The username or password is incorrect");

            return errorDetail;
        }

        if (exception instanceof AccountStatusException) 
        {
            log.warn("Account status exception: {}", exception.getMessage());
            errorDetail = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(403), exception.getMessage());
            errorDetail.setProperty("description", "The account is locked");
        }

        if (exception instanceof AccessDeniedException) 
        {
            log.warn("Access denied exception: {}", exception.getMessage());
            errorDetail = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(403), exception.getMessage());
            errorDetail.setProperty("description", "You are not authorized to access this resource");
        }

        if (exception instanceof SignatureException) 
        {
            log.warn("Signature exception: {}", exception.getMessage());
            errorDetail = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(403), exception.getMessage());
            errorDetail.setProperty("description", "The JWT signature is invalid");
        }

        if (exception instanceof ExpiredJwtException) 
        {
            log.warn("Expired JWT exception: {}", exception.getMessage());
            errorDetail = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(403), exception.getMessage());
            errorDetail.setProperty("description", "The JWT token has expired");
        }

        if (errorDetail == null)
        {
            // Log the real exception (message + stack trace) server-side only -
            // the client only ever sees a fixed, generic message so internal
            // details (library errors, field names, etc.) never leak out.
            log.error("Unexpected exception: {}", exception.getMessage(), exception);
            errorDetail = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(500), "Internal Server Error");
            errorDetail.setProperty("description", "Unknown internal server error.");
        }

        return errorDetail;
    }
}