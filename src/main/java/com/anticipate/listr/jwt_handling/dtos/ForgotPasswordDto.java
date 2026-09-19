package com.anticipate.listr.jwt_handling.dtos;

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
