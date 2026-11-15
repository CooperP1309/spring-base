package com.example.app.authentication.dtos;

public class ForgotPasswordDto
{

    private String email;

    private String password;

    public ForgotPasswordDto()
    {

    }

    public String getEmail()
    {
        return email;
    }

    public ForgotPasswordDto setEmail(String email)
    {
        this.email = email;
        return this;
    }

    /*  Why have I decided to include a password getter when it's never
     *  assigned in the contructor:
     *  
     *  After the forgot password link has been sent, we want to render
     *  the login page. To render the login page, thymeleaf expects a password
     *  getter. When the password reset is requested, it might as well be blank.
     *  Thus, this getter fills out the email provided whilst leaving the password
     *  field blank on the login form.
     */
    public String getPassword()
    {
        return password;
    }
}
