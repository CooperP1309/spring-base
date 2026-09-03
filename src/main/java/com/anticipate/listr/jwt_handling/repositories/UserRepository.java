package com.anticipate.listr.jwt_handling.repositories;

import com.anticipate.listr.jwt_handling.entities.User;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends CrudRepository<User, Integer> 
{
    Optional<User> findByEmail(String email);
    Optional<User> findByEmailVerificationSecret(String emailVerificationSecret);
    Optional<User> findById(String emailVerificationSecret);
    Iterable<User> findAllByRole(com.anticipate.listr.jwt_handling.entities.Role role);
}