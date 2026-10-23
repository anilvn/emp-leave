package com.company.leave.security;

import java.util.List;

import com.company.leave.users.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class AccessPolicyTest {

    private final AccessPolicy accessPolicy = new AccessPolicy();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void currentScopeMapsEverySupportedRole() {
        authenticate(11L, Role.EMPLOYEE);
        assertThat(accessPolicy.currentScope()).isEqualTo(new UserScope.EmployeeOwn(11L));

        authenticate(12L, Role.MANAGER);
        assertThat(accessPolicy.currentScope()).isEqualTo(new UserScope.ManagerTeam(12L));

        authenticate(13L, Role.HR_ADMIN);
        assertThat(accessPolicy.currentScope()).isEqualTo(new UserScope.HrAll());
    }

    @Test
    void currentScopeDeniesMissingOrUnsupportedRoles() {
        SecurityContextHolder.clearContext();
        assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(accessPolicy::currentScope);

        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
                11L, null, List.of(new SimpleGrantedAuthority("ROLE_AUDITOR"))));
        assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(accessPolicy::currentScope);
    }

    @Test
    void ownerManagerAndHrMayPassAndOtherCallersAreDenied() {
        authenticate(10L, Role.EMPLOYEE);
        accessPolicy.requireOwnerManagerOrHr(10L, 20L);

        authenticate(20L, Role.MANAGER);
        accessPolicy.requireOwnerManagerOrHr(10L, 20L);

        authenticate(30L, Role.HR_ADMIN);
        accessPolicy.requireOwnerManagerOrHr(10L, 20L);

        authenticate(40L, Role.EMPLOYEE);
        assertThatExceptionOfType(AccessDeniedException.class)
                .isThrownBy(() -> accessPolicy.requireOwnerManagerOrHr(10L, 20L));

        authenticate(20L, Role.EMPLOYEE);
        assertThatExceptionOfType(AccessDeniedException.class)
                .isThrownBy(() -> accessPolicy.requireOwnerManagerOrHr(10L, 20L));
    }

    @Test
    void requireRoleAllowsOnlyMatchingRoles() {
        authenticate(11L, Role.EMPLOYEE);
        accessPolicy.requireRole(Role.MANAGER, Role.EMPLOYEE);

        assertThatExceptionOfType(AccessDeniedException.class)
                .isThrownBy(() -> accessPolicy.requireRole(Role.MANAGER, Role.HR_ADMIN));
        assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(() -> accessPolicy.requireRole());
    }

    private void authenticate(long userId, Role role) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
                userId, null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }
}
