package com.anticipate.listr.jwt_handling.dtos;

public class DeleteUserDto {

    private String email;

    public String getEmail() {
        return email;
    }

    public DeleteUserDto setEmail(String email) {
        this.email = email;
        return this;
    }

    public DeleteUserDto() {

    }
}
