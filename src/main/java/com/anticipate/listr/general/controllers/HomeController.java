package com.anticipate.listr.general.controllers;

import org.springframework.stereotype.Controller;
import com.anticipate.listr.jwt_handling.dtos.DeleteUserDto;
import com.anticipate.listr.jwt_handling.entities.User;
import com.anticipate.listr.jwt_handling.services.UserService;
import com.anticipate.listr.jwt_handling.services.JwtService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@RequestMapping("/")
@Controller
@Slf4j
public class HomeController 
{
    private final UserService userService;

    private final JwtService jwtService;

    public HomeController(UserService userService,
                            JwtService jwtService) 
    {
        log.info("Initializing HomeController");
        
        this.userService = userService;
        this.jwtService = jwtService;
    }
    
    @GetMapping("/")
    /*  Routing endpoint
     *  
     *  This endpoint checks for a session token.
     *  If none is found, the user is redirected to the landing page.
     *  If a session token is found, the user is redirected to the home page.
     */
    public String routeRequests() 
    {
        // check is user is authenticated
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        /*
         *  when no token is recieved, getAuthentication() returns an 
         *  instance of AnonymousAuthenticationToken.
         *  Thus we need to verifiy that the token is not anonymouse too
         */
        if (authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken))
        {
            return "redirect:/home-page";
        }
        
        return "redirect:/landing-page";
    }

    @GetMapping("landing-page")
    /*  Returns the public landing page  
     *
     *  No authentication is required for this endpoint,
     *  and it serves as the access point for the login page.
     */
    public String showLandingPage() 
    {
        return "landing-page";
    }

    @GetMapping("home-page")
    /*  Returns a personalized home page for the user
     *
     *  Only if a user is authenticated, does this page return
     *   a personalized home page with the user's information.
     */
    public String showHomePage(Model model) 
    {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken))
        {    
            model.addAttribute("username", authentication.getName());
            model.addAttribute("role", authentication.getAuthorities().toString());
        }

        return "home-page";
    }    
}