package com.example.app.authentication.exceptions;

import com.example.app.authentication.entities.User;

public class ExpiredVerificationException extends RuntimeException
{
    private final User user;
    
    public ExpiredVerificationException(User user)
    {
        super("Verification secret has expired for user: " + user.getEmail());
        this.user = user;
    }

    public User getUser()
    {
        return user;
    }
}