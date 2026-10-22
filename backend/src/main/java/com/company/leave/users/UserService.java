package com.company.leave.users;

import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public Optional<UserAccount> authenticate(String username, String rawPassword) {
        return userRepository.findByUsername(username)
                .filter(User::isActive)
                .filter(user -> passwordEncoder.matches(rawPassword, user.getPasswordHash()))
                .map(user -> UserAccount.builder()
                        .id(user.getId())
                        .username(user.getUsername())
                        .role(user.getRole())
                        .mustChangePassword(user.isMustChangePassword())
                        .build());
    }
}
