package com.anticipate.listr.authentication.exceptions;

import com.anticipate.listr.authentication.entities.User;

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