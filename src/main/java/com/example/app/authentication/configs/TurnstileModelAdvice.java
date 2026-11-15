package com.example.app.authentication.configs;

import com.example.app.authentication.services.TurnstileService;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class TurnstileModelAdvice {

    private final TurnstileService turnstile;

    public TurnstileModelAdvice(TurnstileService turnstile) {
        this.turnstile = turnstile;
    }

    @ModelAttribute("turnstileEnabled")
    public boolean turnstileEnabled() {
        return turnstile.isEnabled();
    }

    @ModelAttribute("turnstileSiteKey")
    public String turnstileSiteKey() {
        return turnstile.getSiteKey();
    }
}