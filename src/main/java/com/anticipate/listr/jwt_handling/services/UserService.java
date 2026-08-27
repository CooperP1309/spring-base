package com.anticipate.listr.jwt_handling.services;

import com.anticipate.listr.jwt_handling.entities.User;
import com.anticipate.listr.jwt_handling.repositories.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class UserService 
{
    private final UserRepository userRepository;

    public UserService(UserRepository userRepository)
    {
        this.userRepository = userRepository;
    }

    public List<User> allUsers() 
    {
        List<User> users = new ArrayList<>();

        userRepository.findAll().forEach(users::add);

        return users;
    }

    @Transactional
    public void deleteByEmail(String email) 
    {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NoSuchElementException("No user found with email: " + email));

        userRepository.delete(user);
    }
}