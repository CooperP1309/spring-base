package com.anticipate.listr.jwt_handling.configs;

import com.anticipate.listr.jwt_handling.services.JwtService;
import com.anticipate.listr.jwt_handling.services.SecretGeneratorService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseCookie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CsrfProtectionFilterTest
{
    @Mock
    private JwtService jwtService;

    @Mock
    private SecretGeneratorService secretGeneratorService;

    @Mock
    private AnonymousCsrfCookie anonymousCsrfCookie;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private CsrfProtectionFilter filter;

    @BeforeEach
    void setUp()
    {
        filter = new CsrfProtectionFilter(jwtService, secretGeneratorService, anonymousCsrfCookie);
    }

    @Test
    void getRequest_withNoExistingAnonymousCookie_issuesOneAndStillProceeds() throws Exception
    {
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("Authorization")).thenReturn(null);
        when(request.getCookies()).thenReturn(null);
        when(secretGeneratorService.generateSecureSecret()).thenReturn("fresh-anon-token");
        when(anonymousCsrfCookie.create("fresh-anon-token"))
                .thenReturn(ResponseCookie.from(AnonymousCsrfCookie.NAME, "fresh-anon-token").build());

        filter.doFilterInternal(request, response, filterChain);

        verify(request).setAttribute(CsrfProtectionFilter.REQUEST_ATTRIBUTE, "fresh-anon-token");
        verify(response).addHeader(anyString(), anyString());
        verify(filterChain).doFilter(request, response);
        verify(response, never()).sendError(anyInt(), anyString());
    }

    @Test
    void postWithCookieAuth_whenSubmittedTokenMatchesJwtClaim_proceeds() throws Exception
    {
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("Authorization")).thenReturn(null);
        when(request.getCookies()).thenReturn(new Cookie[] { new Cookie(JwtCookie.NAME, "signed.jwt.value") });
        when(jwtService.extractCsrfToken("signed.jwt.value")).thenReturn("bound-csrf-token");
        when(request.getParameter("_csrf")).thenReturn("bound-csrf-token");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(response, never()).sendError(anyInt(), anyString());
    }

    @Test
    void postWithCookieAuth_whenSubmittedTokenDoesNotMatchJwtClaim_isRejected() throws Exception
    {
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("Authorization")).thenReturn(null);
        when(request.getCookies()).thenReturn(new Cookie[] { new Cookie(JwtCookie.NAME, "signed.jwt.value") });
        when(jwtService.extractCsrfToken("signed.jwt.value")).thenReturn("bound-csrf-token");
        when(request.getParameter("_csrf")).thenReturn("attacker-guessed-token");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain, never()).doFilter(any(), any());
        verify(response).sendError(eq(HttpServletResponse.SC_FORBIDDEN), anyString());
    }

    @Test
    void postWithNoCsrfCookieAndNoSubmittedToken_isRejected() throws Exception
    {
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("Authorization")).thenReturn(null);
        when(request.getCookies()).thenReturn(null);
        when(secretGeneratorService.generateSecureSecret()).thenReturn("brand-new-token");
        when(anonymousCsrfCookie.create("brand-new-token"))
                .thenReturn(ResponseCookie.from(AnonymousCsrfCookie.NAME, "brand-new-token").build());
        when(request.getParameter("_csrf")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain, never()).doFilter(any(), any());
        verify(response).sendError(eq(HttpServletResponse.SC_FORBIDDEN), anyString());
    }

    @Test
    void bearerAuthenticatedRequest_skipsCsrfValidationEntirely() throws Exception
    {
        when(request.getHeader("Authorization")).thenReturn("Bearer some.api.token");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(response, never()).sendError(anyInt(), anyString());
        verify(jwtService, never()).extractCsrfToken(anyString());
    }
}
