package com.company.leave.users;

import jakarta.persistence.LockModeType;
import java.util.Optional;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.Repository;

interface UserRepository extends Repository<User, Long> {

    User save(User user);

    Optional<User> findById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<User> findByUsername(String username);
}
