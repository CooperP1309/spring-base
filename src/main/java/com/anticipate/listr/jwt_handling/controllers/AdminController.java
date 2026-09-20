package com.anticipate.listr.jwt_handling.controllers;

/* ===== local libs ===== */
import com.anticipate.listr.jwt_handling.services.UserService;
import com.anticipate.listr.jwt_handling.services.AuthenticationService;
import com.anticipate.listr.jwt_handling.dtos.SetAccountEnabledDto;

/* ===== spring libs ===== */
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.stereotype.Controller;

/* ===== java libs =====*/
import java.util.NoSuchElementException;
import lombok.extern.slf4j.Slf4j;

@RequestMapping("/admin")
@Controller
@Slf4j
public class AdminController
{
    private final UserService userService;

    private final AuthenticationService authenticationService;

    public AdminController(UserService userService, AuthenticationService authenticationService)
    {
        this.userService = userService;
        this.authenticationService = authenticationService;
    }

    @PostMapping("/delete-user/{userID}")
    /*  Deletes a user by ID
     *
     *  This endpoint interafaces with the user repository
     *  through the UserService module to delete a user by
     *  their ID. Bound as a urlencoded form post from a
     *  per-row form on the dashboard (plain HTML forms can't
     *  submit DELETE), redirecting back once done.
     */
    public String deleteUser(@PathVariable Integer userID)
    {
        try
        {
            userService.deleteUser(userID);
            log.info("User with ID {} deleted successfully", userID);
        }
        catch (NoSuchElementException e)
        {
            log.warn("Attempted to delete missing user with ID {}", userID);
        }

        return "redirect:/admin/dashboard";
    }

    @PostMapping("/set-account-enabled")
    /*  Enables or disables a user account
     *
     *  A user account is considered "enabled" once its email is
     *  verified (see User.isEnabled()). This endpoint lets an admin
     *  flip that flag directly from the dashboard, toggling the
     *  target user's ability to log in. Bound as a urlencoded form
     *  post from a per-row form on the dashboard, redirecting back
     *  once done.
     */
    public String setAccountEnabled(@ModelAttribute SetAccountEnabledDto input)
    {
        if (input.isEnabled()) {
            authenticationService.setEmailAsVerified(input.getEmail());
        } else {
            authenticationService.setEmailAsNotVerified(input.getEmail());
        }

        return "redirect:/admin/dashboard";
    }

    @GetMapping("/dashboard")
    /*  Returns the admin dashboard
     *
     *  Returns the html/web UI for graphically managing the
     *  system, with the current user list rendered server-side.
     */
    public String getDashboard(Model model)
    {
        model.addAttribute("users", userService.allUsers());

        return "admin-page";
    }
}