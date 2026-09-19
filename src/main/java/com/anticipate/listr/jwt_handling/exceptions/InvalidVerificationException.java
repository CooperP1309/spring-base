package com.anticipate.listr.jwt_handling.exceptions;

public class InvalidVerificationException extends RuntimeException
{   
    public InvalidVerificationException()
    {
        super("Verification secret is invalid");
    }
}