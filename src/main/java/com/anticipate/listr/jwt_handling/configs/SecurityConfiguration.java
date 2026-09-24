package com.anticipate.listr.jwt_handling.configs;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfiguration
{
    private final AuthenticationProvider authenticationProvider;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CsrfProtectionFilter csrfProtectionFilter;

    public SecurityConfiguration(
        JwtAuthenticationFilter jwtAuthenticationFilter,
        CsrfProtectionFilter csrfProtectionFilter,
        AuthenticationProvider authenticationProvider
    )
    {
        this.authenticationProvider = authenticationProvider;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.csrfProtectionFilter = csrfProtectionFilter;
    }

    @Bean
    /*  Configuration of the Security Filter Chain
     *
     *  This repo uses jwt session token cookies for authorisation.  
     *  For csrf protection for the cookie implementation, this uses a
     *  non spring native csrf token implementaion.
     *  Hence, explicitly I've explicitly disabled it the spring native csrf protection.
     *  Csrf validation comes after jwt token validation (see end of chain)
     */
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception
    {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                
                // keeping js matchers incase we want I need them for a future project
                .requestMatchers("/js/**", "/css/**", "/images/**", "/webjars/**", "/favicon.ico").permitAll()
                .requestMatchers("/", "/landing-page").permitAll()
                .requestMatchers(
                    "/auth/login", "/auth/login-page",
                    "/auth/register", "/auth/register-page",
                    "/auth/forgot-password",
                    "/auth/verify/**", "/auth/reset-password/**"
                ).permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authenticationProvider(authenticationProvider)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(csrfProtectionFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    /*  Cors config (duh)
     *  
     *  Explicitly left out auth headers to harden the csrf protected cookie route.
     *  (Even though auth headers aren't prone to csrf)
     *  This is just to enforce uniformity in how authentication is implemented.
     */
    CorsConfigurationSource corsConfigurationSource() 
    {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(List.of("http://localhost:8005"));
        configuration.setAllowedMethods(List.of("GET","POST"));
        configuration.setAllowedHeaders(List.of("Content-Type"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/**",configuration);

        return source;
    }
}