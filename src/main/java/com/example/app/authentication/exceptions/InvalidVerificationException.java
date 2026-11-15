package com.example.app.authentication.exceptions;

public class InvalidVerificationException extends RuntimeException
{   
    public InvalidVerificationException()
    {
        super("Verification secret is invalid");
    }
}