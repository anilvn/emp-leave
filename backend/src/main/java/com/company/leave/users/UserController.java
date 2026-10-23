package com.company.leave.users;

import java.util.List;

import jakarta.validation.Valid;
import com.company.leave.security.AccessPolicy;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;
    private final AccessPolicy accessPolicy;

    UserController(UserService userService, AccessPolicy accessPolicy) {
        this.userService = userService;
        this.accessPolicy = accessPolicy;
    }

    @PostMapping
    public ResponseEntity<UserResponse> createUser(
            Authentication authentication, @Valid @RequestBody UserCreateRequest request) {
        accessPolicy.requireRole(Role.HR_ADMIN);
        long actorId = (Long) authentication.getPrincipal();
        UserResponse created = userService.createUser(request, actorId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<List<UserResponse>> listUsers() {
        accessPolicy.requireRole(Role.HR_ADMIN);
        return ResponseEntity.ok(userService.listUsers());
    }

    @GetMapping("/managers")
    public ResponseEntity<List<ManagerOptionResponse>> listManagerOptions() {
        accessPolicy.requireRole(Role.HR_ADMIN);
        return ResponseEntity.ok(userService.listManagerOptions());
    }
}
