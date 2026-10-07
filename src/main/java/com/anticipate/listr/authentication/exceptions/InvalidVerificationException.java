package com.anticipate.listr.authentication.exceptions;

public class InvalidVerificationException extends RuntimeException
{   
    public InvalidVerificationException()
    {
        super("Verification secret is invalid");
    }
}