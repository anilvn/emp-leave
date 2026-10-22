package com.company.leave.users;

import java.util.Optional;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String dummyPasswordHash;

    UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional(readOnly = true)
    public Optional<UserAccount> authenticate(String username, String rawPassword) {
        Optional<User> candidate = userRepository.findByUsername(username);
        if (candidate.isEmpty()) {
            passwordEncoder.matches(rawPassword, dummyPasswordHash);
            return Optional.empty();
        }

        User user = candidate.get();
        boolean passwordMatches = passwordEncoder.matches(rawPassword, user.getPasswordHash());
        if (!user.isActive() || !passwordMatches) {
            return Optional.empty();
        }

        return Optional.of(UserAccount.builder()
                .id(user.getId())
                .username(user.getUsername())
                .role(user.getRole())
                .mustChangePassword(user.isMustChangePassword())
                .build());
    }
}
