package com.template.example.authentication.responses;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/*  Response body from Cloudflare's Turnstile siteverify endpoint
 *
 *  Example:
 *  {"challenge_ts":"2026-11-07T06:06:17.045Z","error-codes":[],"hostname":"example.com",
 *   "metadata":{"result_with_testing_key":true},"success":true}
 *
 *  Unknown fields are ignored so new fields from Cloudflare don't break deserialisation.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TurnstileVerifyResponse
{
    private boolean success;

    @JsonProperty("challenge_ts")
    private String challengeTs;

    private String hostname;

    @JsonProperty("error-codes")
    private List<String> errorCodes = List.of();

    private String action;

    private String cdata;

    private Map<String, Object> metadata = Map.of();

    public boolean isSuccess()
    {
        return success;
    }

    public String getChallengeTs()
    {
        return challengeTs;
    }

    public String getHostname()
    {
        return hostname;
    }

    public List<String> getErrorCodes()
    {
        return errorCodes;
    }

    public String getAction()
    {
        return action;
    }

    public String getCdata()
    {
        return cdata;
    }

    public Map<String, Object> getMetadata()
    {
        return metadata;
    }
}
