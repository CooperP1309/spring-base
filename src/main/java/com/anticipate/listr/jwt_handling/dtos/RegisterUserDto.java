package com.anticipate.listr.jwt_handling.dtos;

public class RegisterUserDto 
{
    private String email;

    private String password;

    private String fullName;

    private boolean emailVerified;

    public String getEmail() 
    {
        return email;
    }

    public RegisterUserDto setEmail(String email) 
    {
        this.email = email;
        return this;
    }

    public String getPassword() 
    {
        return password;
    }

    public RegisterUserDto setPassword(String password) 
    {
        this.password = password;
        return this;
    }

    public String getFullName() 
    {
        return fullName;
    }

    public RegisterUserDto setFullName(String fullName)
    {
        this.fullName = fullName;
        return this;
    }

    public boolean getEmailVerified() 
    {
        return emailVerified;
    }

    public RegisterUserDto setEmailVerified(boolean emailVerified) 
    {
        this.emailVerified = emailVerified;
        return this;
    }

    public RegisterUserDto() 
    {
        
    }

    public RegisterUserDto(String email, String fullName, String password) 
    {
        this.email = email;
        this.fullName = fullName;
        this.password = password;
    }
}
