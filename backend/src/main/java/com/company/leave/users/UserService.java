package com.company.leave.users;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.company.leave.common.ApiException;
import com.company.leave.common.ErrorCode;
import com.company.leave.security.SessionRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SessionRegistry sessionRegistry;
    private final String dummyPasswordHash;

    UserService(
            UserRepository userRepository, PasswordEncoder passwordEncoder, SessionRegistry sessionRegistry) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.sessionRegistry = sessionRegistry;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional(noRollbackFor = ApiException.class)
    public Optional<UserAccount> authenticate(String username, String rawPassword) {
        Optional<User> candidate = userRepository.findByUsername(username);
        if (candidate.isEmpty()) {
            passwordEncoder.matches(rawPassword, dummyPasswordHash);
            return Optional.empty();
        }

        User user = candidate.get();
        Instant now = Instant.now();
        if (user.isLockedAt(now)) {
            throw accountLocked(user);
        }
        boolean expiredLock = user.getLockedUntil() != null;
        if (expiredLock) {
            user.clearFailedLogins();
        }

        boolean passwordMatches = passwordEncoder.matches(rawPassword, user.getPasswordHash());
        if (!passwordMatches) {
            if (user.recordFailedLogin(now)) {
                userRepository.save(user);
                sessionRegistry.revokeAllForUser(user.getId());
                throw accountLocked(user);
            }
            userRepository.save(user);
            return Optional.empty();
        }

        if (!user.isActive()) {
            if (expiredLock) {
                userRepository.save(user);
            }
            return Optional.empty();
        }

        user.clearFailedLogins();
        userRepository.save(user);

        return Optional.of(UserAccount.builder()
                .id(user.getId())
                .username(user.getUsername())
                .role(user.getRole())
                .mustChangePassword(user.isMustChangePassword())
                .build());
    }

    private ApiException accountLocked(User user) {
        return new ApiException(
                HttpStatus.LOCKED,
                ErrorCode.ACCOUNT_LOCKED,
                "Account locked until " + user.getLockedUntil() + " after "
                        + User.MAX_FAILED_LOGINS + " failed login attempts.");
    }
}
