package com.company.leave.security;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.company.leave.audit.AuditService;
import com.company.leave.users.UserService;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Enforces FR20: while the authenticated caller's {@code mustChangePassword} is true,
 * every endpoint except the escape routes below is refused with the {@code PasswordChangeRequired}
 * body (openapi.yaml, {@code PasswordChangeRequired} response component). {@code User} has no
 * cache, so this loads the row fresh on every request via {@link UserService}.
 *
 * <p>Because a short-circuited request here never reaches {@link AccessLogFilter} later in the
 * chain, this filter records its own {@code access_log} denial — mirroring that filter's
 * {@code resource} string convention (method, path, {@code X-Forwarded-Prefix} handling) so the
 * two filters' rows stay consistent.
 */
public class PasswordChangeRequiredFilter extends OncePerRequestFilter {

    private static final Set<String> ESCAPE_ROUTES =
            Set.of("/auth/login", "/auth/logout", "/auth/change-password", "/actuator/health");

    private static final Pattern NUMERIC_PATH_SEGMENT = Pattern.compile("(?:^|/)(\\d+)(?:/|$)");

    private final UserService userService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    PasswordChangeRequiredFilter(UserService userService, AuditService auditService, ObjectMapper objectMapper) {
        this.userService = userService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Long userId = authenticatedUserId(authentication);
        String path = requestPath(request);
        if (userId == null || ESCAPE_ROUTES.contains(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (userService.mustChangePassword(userId)) {
            String loggedPath = loggedPath(request, path);
            auditService.recordAccess(
                    userId, request.getMethod() + " " + loggedPath, numericPathId(loggedPath), false);
            writePasswordChangeRequired(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private Long authenticatedUserId(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof Number principal)) {
            return null;
        }
        return principal.longValue();
    }

    private String requestPath(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        return contextPath != null && !contextPath.isEmpty() && requestUri.startsWith(contextPath)
                ? requestUri.substring(contextPath.length())
                : requestUri;
    }

    private String loggedPath(HttpServletRequest request, String path) {
        String forwardedPrefix = request.getHeader("X-Forwarded-Prefix");
        if ("/api".equals(forwardedPrefix) && !isApiPath(path)) {
            return forwardedPrefix + (path.startsWith("/") ? path : "/" + path);
        }
        return path;
    }

    private boolean isApiPath(String path) {
        return "/api".equals(path) || path.startsWith("/api/");
    }

    private Long numericPathId(String path) {
        Matcher matcher = NUMERIC_PATH_SEGMENT.matcher(path);
        Long resourceId = null;
        while (matcher.find()) {
            try {
                resourceId = Long.valueOf(matcher.group(1));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return resourceId;
    }

    private void writePasswordChangeRequired(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType("application/problem+json");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "about:blank");
        body.put("title", "Password change required");
        body.put("status", HttpStatus.FORBIDDEN.value());
        body.put("detail", "Your temporary password must be changed before you can use this resource.");
        body.put("code", "PASSWORD_CHANGE_REQUIRED");
        objectMapper.writeValue(response.getWriter(), body);
    }
}
