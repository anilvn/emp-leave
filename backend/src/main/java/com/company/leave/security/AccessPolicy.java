package com.company.leave.security;

import java.util.EnumSet;
import java.util.Set;

import com.company.leave.users.Role;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class AccessPolicy {

    AccessPolicy() {}

    public void requireOwnerManagerOrHr(long ownerId, long managerId) {
        Caller caller = currentCaller();
        if (caller.role() != Role.HR_ADMIN
                && caller.userId() != ownerId
                && (caller.role() != Role.MANAGER || caller.userId() != managerId)) {
            throw denied();
        }
    }

    public UserScope currentScope() {
        Caller caller = currentCaller();
        return switch (caller.role()) {
            case EMPLOYEE -> new UserScope.EmployeeOwn(caller.userId());
            case MANAGER -> new UserScope.ManagerTeam(caller.userId());
            case HR_ADMIN -> new UserScope.HrAll();
        };
    }

    public void requireRole(Role... allowedRoles) {
        Caller caller = currentCaller();
        if (allowedRoles == null) {
            throw denied();
        }
        for (Role allowedRole : allowedRoles) {
            if (caller.role() == allowedRole) {
                return;
            }
        }
        throw denied();
    }

    private Caller currentCaller() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof Number principal)) {
            throw denied();
        }

        Set<Role> roles = EnumSet.noneOf(Role.class);
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String value = authority.getAuthority();
            if (value != null && value.startsWith("ROLE_")) {
                try {
                    roles.add(Role.valueOf(value.substring("ROLE_".length())));
                } catch (IllegalArgumentException ignored) {
                    // Unsupported authorities do not grant a policy role.
                }
            }
        }
        if (roles.size() != 1) {
            throw denied();
        }
        return new Caller(principal.longValue(), roles.iterator().next());
    }

    private AccessDeniedException denied() {
        return new AccessDeniedException("Access is denied");
    }

    private record Caller(long userId, Role role) {}
}
