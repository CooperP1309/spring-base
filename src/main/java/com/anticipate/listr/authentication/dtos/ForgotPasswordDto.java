package com.anticipate.listr.authentication.dtos;

public class ForgotPasswordDto
{

    private String email;

    public String getEmail()
    {
        return email;
    }

    public ForgotPasswordDto setEmail(String email)
    {
        this.email = email;
        return this;
    }

    public ForgotPasswordDto()
    {

    }
}
