package com.anticipate.listr.jwt_handling.exceptions;

import com.anticipate.listr.jwt_handling.entities.User;

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