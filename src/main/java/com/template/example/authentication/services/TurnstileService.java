package com.template.example.authentication.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestClient;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.template.example.authentication.responses.TurnstileVerifyResponse;

@Service
@Slf4j
public class TurnstileService 
{
    private final boolean turnstileActive;

    private String siteKey;

    private String secretKey;

    private final RestClient restClient;

    private static final String VERIFY_URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify";

    public TurnstileService(@Value("${captcha.turnstile.enabled}") boolean turnstileActive,
                            @Value("${captcha.turnstile.site-key}") String siteKey,
                            @Value("${captcha.turnstile.secret-key}") String secretKey) 
    {
        this.turnstileActive = turnstileActive;
        this.siteKey = null;
        this.secretKey = null;

        this.restClient = RestClient.create();

        if (this.turnstileActive)
        {
            if (siteKey.isBlank() || secretKey.isBlank())   // Don't bother to check if == null, isBlank() will just throw for us anyway
            {
                throw new IllegalStateException("Missing site-key or secret-key");
            }

            this.siteKey = siteKey;
            this.secretKey = secretKey;
        }
    }

    /*  Returns required fields for HTML rendering
     *
     *  These fields are required by the model advice to allow
     *  the browser to run the captcha.
     */
    public boolean isEnabled()
    {
        return this.turnstileActive;
    }
    public String getSiteKey()
    {
        return this.siteKey;
    }

    /*  Verify against Cloudflare
     *
     *  Once a user has received a turnstile token from cloud flare, 
     *  it's sent here from the controller. This function uses the .env
     *  secret in conjunction (not AND-ing) with the response token
     *  to verify if the request is allowed to continue.
     */
    public boolean verify(String token, String remoteIp)
    {
        if (!this.turnstileActive)
        {
            return true;
        }

        if (token == null || token.isBlank())
        {
            return false;
        }

        log.info("VERIFYING TOKEN {}", token);

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        
        formData.add("secret", secretKey);
        formData.add("response", token);

        TurnstileVerifyResponse response =
        
            restClient.post()
                .uri(VERIFY_URL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(formData)
                .retrieve()
                .body(TurnstileVerifyResponse.class);

        log.info("RESPONSE FROM CLOUD FLARE {}", response.isSuccess());

        return true; // TODO: REMOVE LATER
    }
}
