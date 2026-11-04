package com.template.example.authentication.repositories;

import com.template.example.authentication.entities.User;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends CrudRepository<User, Integer> 
{
    Optional<User> findByEmail(String email);
    Optional<User> findByEmailVerificationSecret(String emailVerificationSecret);
    Optional<User> findById(String emailVerificationSecret);
    Iterable<User> findAllByRole(com.template.example.authentication.entities.Role role);
}