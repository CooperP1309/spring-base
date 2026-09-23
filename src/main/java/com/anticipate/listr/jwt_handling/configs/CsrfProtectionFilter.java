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

/*  Synchronizer-token CSRF defence, sized to match this app: every request
 *  is server-rendered, submitted forms only, no JS/AJAX. Rather than a
 *  general-purpose double-submit cookie (Spring's CookieCsrfTokenRepository,
 *  tried and reverted earlier on this branch), the expected token is
 *  derived from whatever already authenticates the request:
 *
 *   - Authenticated via the "jwt" cookie -> expected token is the "csrf"
 *     claim embedded in that same JWT (see JwtService). Nothing extra to
 *     store; it rotates automatically with every login.
 *   - Not yet authenticated (login/register/forgot/reset-password forms)
 *     -> expected token is a short-lived anonymous cookie (AnonymousCsrfCookie),
 *     handed out the first time one of those pages is rendered.
 *
 *  There is no "Authorization: Bearer" exemption - JwtAuthenticationFilter
 *  no longer accepts that header at all (cookie-only), since an unvalidated
 *  header's mere presence was previously enough to skip this check entirely.
 *
 *  Either way, the resolved token is stashed on the request so
 *  CsrfModelAttributeAdvice can hand it to every view as ${csrfToken} for
 *  forms to embed as a hidden "_csrf" field.
 */
@Slf4j
@Component
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
        String jwtCookieValue = readCookie(request, JwtCookie.NAME);
        String expectedToken;

        if (jwtCookieValue != null)
        {
            // A malformed/expired jwt cookie would already have been rejected
            // upstream by JwtAuthenticationFilter, which stops the chain
            // before this filter ever runs - safe to trust it here.
            expectedToken = jwtService.extractCsrfToken(jwtCookieValue);
        }
        else
        {
            expectedToken = resolveAnonymousToken(request, response);
        }

        request.setAttribute(REQUEST_ATTRIBUTE, expectedToken);

        if (SAFE_METHODS.contains(request.getMethod()))
        {
            filterChain.doFilter(request, response);
            return;
        }

        String submittedToken = request.getParameter(FORM_FIELD);

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
