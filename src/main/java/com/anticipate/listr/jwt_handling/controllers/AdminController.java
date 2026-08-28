package com.anticipate.listr.jwt_handling.controllers;

/* ===== local libs ===== */
import com.anticipate.listr.jwt_handling.entities.User;
import com.anticipate.listr.jwt_handling.dtos.LoginUserDto;
import com.anticipate.listr.jwt_handling.dtos.RegisterUserDto;
import com.anticipate.listr.jwt_handling.responses.LoginResponse;
import com.anticipate.listr.jwt_handling.services.AuthenticationService;
import com.anticipate.listr.jwt_handling.services.JwtService;
import com.anticipate.listr.jwt_handling.services.UserService;

/* ===== spring libs ===== */
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.stereotype.Controller;

/* ===== java libs =====*/
import java.util.List;
import java.util.Optional;

@RequestMapping("/admin")
@Controller
public class AdminController 
{
    private final JwtService jwtService;
    
    private final AuthenticationService authenticationService;

    private final UserService userService;

    public AdminController(JwtService jwtService,
                                    AuthenticationService authenticationService, 
                                    UserService userService) 
    {
        this.jwtService = jwtService;
        this.authenticationService = authenticationService;
        this.userService = userService;
    }

    @GetMapping("/get-users")
    /*  Retrieves all users
     *
     *  This endpoint interafaces with the user repository
     *  to return a json body of all users in the system.
     */
    public ResponseEntity<List<User>> getAllUsers()
    {
        List <User> users = userService.allUsers();

        return ResponseEntity.ok(users);
    }

    @DeleteMapping("/delete-user/{userID}")
    public ResponseEntity<Void> deleteUser(@PathVariable Integer userID)
    {
        userService.deleteUser(userID);

        return ResponseEntity.noContent().build();
    }
}