package com.company.leave.users;

import java.util.Optional;

import org.springframework.data.repository.Repository;

interface UserRepository extends Repository<User, Long> {

    User save(User user);

    Optional<User> findById(Long id);

    Optional<User> findByUsername(String username);
}
