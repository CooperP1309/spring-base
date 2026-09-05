package com.anticipate.listr.jwt_handling.controllers;

/* ===== local libs ===== */
import com.anticipate.listr.jwt_handling.entities.User;
import com.anticipate.listr.jwt_handling.services.UserService;

/* ===== spring libs ===== */
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.stereotype.Controller;

/* ===== java libs =====*/
import java.util.List;
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

    @GetMapping("/get-users")
    /*  Retrieves all users
     *
     *  This endpoint interafaces with the user repository
     *  through the UserService module to return a json body 
     *  of all users in the system.
     */
    public ResponseEntity<List<User>> getAllUsers()
    {
        List <User> users = userService.allUsers();

        log.info("Retrieved {} users from the system", users.size());

        return ResponseEntity.ok(users);
    }

    @DeleteMapping("/delete-user/{userID}")
    /*  Deletes a user by ID
     *
     *  This endpoint interafaces with the user repository
     *  through the UserService module to delete a user by
     *  their ID.
     */
    public ResponseEntity<Void> deleteUser(@PathVariable Integer userID)
    {
        userService.deleteUser(userID);

        log.info("User with ID {} deleted successfully", userID);

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/dashboard")
    /*  Returns the admin dashboard
     *  
     *  Returns the html/web UI for graphically managing the
     *  system. This interfaces with all of the AdminController
     *  endpoints.
     */
    public String getDashboard()
    {
        return "admin-page";
    }
}