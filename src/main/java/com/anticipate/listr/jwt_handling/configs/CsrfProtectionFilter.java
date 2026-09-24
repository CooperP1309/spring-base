package com.anticipate.listr.jwt_handling.configs;

import com.anticipate.listr.jwt_handling.services.JwtService;
import com.anticipate.listr.jwt_handling.services.SecretGeneratorService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;

@Slf4j
@Component
/*  NOTE TO SELF FOR CUSTOM FILTER CLASSES
 *  
 *  This class inherits the "abstract" doInternalFilter() function from 
 *  OncePerRequestFilter. Abstract is a keyword in defining the function. 
 *  It is literally written like so in the parent class:
 * 
 *      protected abstract void doFilterInternal(
 *      HttpServletRequest request, HttpServletResponse response, FilterChain chain);
 *       
 *  In this class, we user "@override" to make our implementation of it. Our implementation
 *  must AT LEAST adhere to the function signature. Otherwise, we're free to do what we want
 *  with it.
 * 
 *  We could theoretically also make this class abstract and add aditional abstract functions.
 *  But the first non abstract child class inheriting doInternalFilter() MUST properly define it. 
 */ 
public class CsrfProtectionFilter extends OncePerRequestFilter
{
    public static final String REQUEST_ATTRIBUTE = "csrfToken";

    private static final String FORM_FIELD = "_csrf";

    private static final Set<String> SAFE_METHODS = Set.of(
            HttpMethod.GET.name(), HttpMethod.HEAD.name(),
            HttpMethod.OPTIONS.name(), HttpMethod.TRACE.name());

    private final JwtService jwtService;
    private final SecretGeneratorService secretGeneratorService;
    private final AnonymousCsrfCookie anonymousCsrfCookie;

    public CsrfProtectionFilter(
            JwtService jwtService,
            SecretGeneratorService secretGeneratorService,
            AnonymousCsrfCookie anonymousCsrfCookie)
    {
        this.jwtService = jwtService;
        this.secretGeneratorService = secretGeneratorService;
        this.anonymousCsrfCookie = anonymousCsrfCookie;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException
    {
        /*  readCookie() literally extracts the cookie value from
         *  the intercepted http request passed from the spring security filter
         *  (Assuming the name matches that in jwtCookie.Name)
         */
        String jwtCookieValue = readCookie(request, JwtCookie.NAME);
        String expectedToken;

        if (jwtCookieValue != null)
        {
            /*  This validation proceeds the jwt token validation. 
             *  Hence, we can assume a non-malformed token by this point.
             *
             *  See JwtService for documentation on how the below works.
             */
            expectedToken = jwtService.extractCsrfToken(jwtCookieValue);
        }
        else
        {
            expectedToken = resolveAnonymousToken(request, response);
        }

        request.setAttribute(REQUEST_ATTRIBUTE, expectedToken);

        // exit early if GET request
        if (SAFE_METHODS.contains(request.getMethod()))
        {
            filterChain.doFilter(request, response);
            return;
        }

        /*  NOTE FOR FUTURE ME: 
         *  When a POST action sends over the csrf token, the token
         *  is sent in the body of the form-urlencoded request
         *  
         *  Because it's a form encoded body, req.getParam() here reads
         *  BOTH the url params and the body. They both match the formatting:
         *  "email=...&password=...&_csrf..."
         */
        String submittedToken = request.getParameter(FORM_FIELD);

        /*  the final step... 
         *  checking that the csrf token from the form matches that of the jwt payload
         */
        if (!tokensMatch(expectedToken, submittedToken))
        {
            log.warn("CSRF token missing or invalid for {} {}", request.getMethod(), request.getRequestURI());
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid or missing CSRF token");
            return;
        }

        filterChain.doFilter(request, response);
    }

    /*  Returns the existing anonymous token if the browser already has one,
     *  otherwise mints a fresh one and sets it as a response cookie. A POST
     *  that arrives with no prior cookie (e.g. a forged cross-site submission
     *  with no legitimate page ever rendered) simply won't have a matching
     *  submitted value and gets rejected by tokensMatch() below.
     */
    private String resolveAnonymousToken(HttpServletRequest request, HttpServletResponse response)
    {
        String existing = readCookie(request, AnonymousCsrfCookie.NAME);

        if (existing != null)
        {
            return existing;
        }

        String freshToken = secretGeneratorService.generateSecureSecret();
        response.addHeader(HttpHeaders.SET_COOKIE, anonymousCsrfCookie.create(freshToken).toString());

        return freshToken;
    }

    private String readCookie(HttpServletRequest request, String name)
    {
        if (request.getCookies() == null)
        {
            return null;
        }

        for (Cookie cookie : request.getCookies())
        {
            if (name.equals(cookie.getName()) && !cookie.getValue().isEmpty())
            {
                return cookie.getValue();
            }
        }

        return null;
    }

    private boolean tokensMatch(String expected, String submitted)
    {
        if (expected == null || submitted == null)
        {
            return false;
        }

        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                submitted.getBytes(StandardCharsets.UTF_8));
    }
}
