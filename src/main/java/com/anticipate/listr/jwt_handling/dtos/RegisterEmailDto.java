package com.anticipate.listr.jwt_handling.dtos;

public class RegisterEmailDto {
    
    private String email;

    public String getEmail() {
        return email;
    }

    public RegisterEmailDto setEmail(String email) {
        this.email = email;
        return this;
    }
}