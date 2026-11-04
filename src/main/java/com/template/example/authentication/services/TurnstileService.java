package com.template.example.authentication.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class TurnstileService 
{
    private final boolean turnstileActive;

    private String siteKey;

    private String secretKey;

    public TurnstileService(@Value("${captcha.turnstile.enabled}") boolean turnstileActive,
                            @Value("${captcha.turnstile.site-key}") String siteKey,
                            @Value("${captcha.turnstile.secret-key}") String secretKey) 
    {
        this.turnstileActive = turnstileActive;
        this.siteKey = null;
        this.secretKey = null;

        if (this.turnstileActive)
        {
            if (siteKey.isBlank() || secretKey.isBlank())
            {
                throw new IllegalStateException("Missing site-key or secret-key");
            }

            this.siteKey = siteKey;
            this.secretKey = secretKey;
        }
    }
}
