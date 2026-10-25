package com.company.leave.users;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.company.leave.audit.AuditService;
import com.company.leave.common.ApiException;
import com.company.leave.common.ErrorCode;
import com.company.leave.security.SessionRegistry;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private static final Pattern NEW_PASSWORD_PATTERN = Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d).{8,72}$");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SessionRegistry sessionRegistry;
    private final AuditService auditService;
    private final String dummyPasswordHash;
    private final SecureRandom secureRandom = new SecureRandom();

    UserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            SessionRegistry sessionRegistry,
            AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.sessionRegistry = sessionRegistry;
        this.auditService = auditService;
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

    @Transactional
    public UserResponse createUser(UserCreateRequest request, long actorId) {
        validateManager(request.getRole(), request.getManagerId());

        String username = request.getUsername().trim();
        if (userRepository.existsByUsername(username)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.VALIDATION_FAILED,
                    "Username " + username + " is already in use.");
        }

        User user = User.builder()
                .username(username)
                .passwordHash(passwordEncoder.encode(request.getTemporaryPassword()))
                .role(request.getRole())
                .managerId(request.getManagerId())
                .mustChangePassword(true)
                .active(true)
                .build();
        try {
            user = userRepository.save(user);
        } catch (DataIntegrityViolationException ex) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.VALIDATION_FAILED,
                    "Username " + request.getUsername() + " is already in use.");
        }

        auditService.recordEvent("USER", user.getId(), "CREATE", actorId, null);

        return toResponse(user);
    }

    @Transactional
    public void changePassword(long userId, String currentPassword, String newPassword) {
        if (!NEW_PASSWORD_PATTERN.matcher(newPassword).matches()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.VALIDATION_FAILED,
                    "Password must be at least 8 characters and include both letters and numbers.");
        }

        User user = userRepository
                .findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "User not found."));
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED, ErrorCode.INVALID_CREDENTIALS, "Current password is incorrect.");
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.VALIDATION_FAILED,
                    "newPassword must be different from the current password.");
        }

        user.changePassword(passwordEncoder.encode(newPassword), false);
        userRepository.save(user);
        auditService.recordEvent("USER", userId, "CHANGE_PASSWORD", userId, null);
    }

    @Transactional
    public String resetPassword(long targetUserId, long actorId) {
        User user = userRepository
                .findById(targetUserId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "User " + targetUserId + " does not exist."));

        String temporaryPassword = generateTemporaryPassword();
        user.changePassword(passwordEncoder.encode(temporaryPassword), true);
        userRepository.save(user);
        sessionRegistry.revokeAllForUser(targetUserId);
        auditService.recordEvent("USER", targetUserId, "RESET_PASSWORD", actorId, null);
        return temporaryPassword;
    }

    @Transactional(readOnly = true)
    public boolean mustChangePassword(long userId) {
        return userRepository.findById(userId).map(User::isMustChangePassword).orElse(false);
    }

    private String generateTemporaryPassword() {
        // 12 digits (~40 bits of entropy), shaped like the contract's own Temp#8841 example.
        return String.format("Temp#%012d", secureRandom.nextLong(1_000_000_000_000L));
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listUsers() {
        return userRepository.findAll().stream().map(this::toResponse).toList();
    }

    /**
     * Returns the user's {@code managerId}, or {@code null} for top-level HR users.
     * This is the read seam other modules call to set {@code approverId} on a leave
     * request (AD-2).
     */
    @Transactional(readOnly = true)
    public Long getManagerId(long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "User " + userId + " not found."));
        return user.getManagerId();
    }

    @Transactional(readOnly = true)
    public Map<Long, String> getUsernamesByIds(Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findByIdIn(userIds).stream()
                .collect(Collectors.toUnmodifiableMap(User::getId, User::getUsername));
    }

    @Transactional(readOnly = true)
    public List<ManagerOptionResponse> listManagerOptions() {
        return userRepository.findByRoleIn(List.of(Role.MANAGER, Role.HR_ADMIN)).stream()
                .map(user -> ManagerOptionResponse.builder()
                        .id(user.getId())
                        .username(user.getUsername())
                        .role(user.getRole())
                        .build())
                .toList();
    }

    private void validateManager(Role role, Long managerId) {
        if (managerId == null) {
            if (role == Role.HR_ADMIN) {
                return;
            }
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.VALIDATION_FAILED,
                    "managerId is required when role is EMPLOYEE or MANAGER.");
        }
        User manager = userRepository
                .findById(managerId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.BAD_REQUEST,
                        ErrorCode.VALIDATION_FAILED,
                        "managerId " + managerId + " does not refer to an existing user."));
        if (manager.getRole() != Role.MANAGER && manager.getRole() != Role.HR_ADMIN) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.VALIDATION_FAILED,
                    "User " + manager.getId() + " (" + manager.getUsername() + ") has role "
                            + manager.getRole() + " and cannot be selected as a Manager.");
        }
    }

    private UserResponse toResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .role(user.getRole())
                .managerId(user.getManagerId())
                .mustChangePassword(user.isMustChangePassword())
                .active(user.isActive())
                .build();
    }
}
