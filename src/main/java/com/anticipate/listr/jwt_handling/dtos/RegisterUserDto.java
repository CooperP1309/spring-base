package com.anticipate.listr.jwt_handling.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class RegisterUserDto 
{
    private String email;

    @NotBlank
    @Size(min = 12, max = 100, message = "Password must be at least 12 characters long")
    @Pattern(
        regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z]).*$",
        message = "Password must contain at least one digit, one lowercase, and one uppercase letter."
    )
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
