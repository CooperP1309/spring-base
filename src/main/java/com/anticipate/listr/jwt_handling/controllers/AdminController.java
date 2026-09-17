package com.anticipate.listr.jwt_handling.controllers;

/* ===== local libs ===== */
import com.anticipate.listr.jwt_handling.services.UserService;

/* ===== spring libs ===== */
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    public AdminController(UserService userService)
    {
        this.userService = userService;
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