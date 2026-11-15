package com.example.app.authentication.repositories;

import com.example.app.authentication.entities.User;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends CrudRepository<User, Integer> 
{
    Optional<User> findByEmail(String email);
    Optional<User> findByEmailVerificationSecret(String emailVerificationSecret);
    Optional<User> findById(String emailVerificationSecret);
    Iterable<User> findAllByRole(com.example.app.authentication.entities.Role role);
}