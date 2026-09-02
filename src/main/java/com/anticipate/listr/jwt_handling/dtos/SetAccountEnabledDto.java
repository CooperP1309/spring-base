package com.anticipate.listr.jwt_handling.dtos;

public class SetAccountEnabledDto
{

    private String email;

    private boolean enabled;

    public String getEmail()
    {
        return email;
    }

    public SetAccountEnabledDto setEmail(String email)
    {
        this.email = email;
        return this;
    }

    public boolean isEnabled()
    {
        return enabled;
    }

    public SetAccountEnabledDto setEnabled(boolean enabled)
    {
        this.enabled = enabled;
        return this;
    }

    public SetAccountEnabledDto()
    {

    }
}
