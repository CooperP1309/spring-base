package com.anticipate.listr.jwt_handling.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class ResetPasswordDto
{
    @NotBlank
    @Size(min = 12, max = 100, message = "Password must be at least 12 characters long")
    @Pattern(
        regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z]).*$",
        message = "Password must contain at least one digit, one lowercase, and one uppercase letter."
    )
    private String password;

    public String getPassword()
    {
        return password;
    }

    public ResetPasswordDto setPassword(String password)
    {
        this.password = password;
        return this;
    }

    public ResetPasswordDto()
    {

    }
}
