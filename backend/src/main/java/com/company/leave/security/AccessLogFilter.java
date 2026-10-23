package com.company.leave.security;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.company.leave.audit.AuditService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class AccessLogFilter extends OncePerRequestFilter {

    private static final Pattern NUMERIC_PATH_SEGMENT = Pattern.compile("(?:^|/)(\\d+)(?:/|$)");

    private final AuditService auditService;

    AccessLogFilter(AuditService auditService) {
        this.auditService = auditService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Long actorId = authenticatedUserId(authentication);
        String path = requestPath(request);
        if (actorId == null || !isApiPath(path) || isPublicApiRequest(request.getMethod(), path)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            auditService.recordAccess(
                    actorId,
                    request.getMethod() + " " + path,
                    numericPathId(path),
                    response.getStatus() != HttpStatus.FORBIDDEN.value());
        }
    }

    private Long authenticatedUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof Number principal)) {
            return null;
        }
        return principal.longValue();
    }

    private String requestPath(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        String path = contextPath != null && !contextPath.isEmpty() && requestUri.startsWith(contextPath)
                ? requestUri.substring(contextPath.length())
                : requestUri;
        String forwardedPrefix = request.getHeader("X-Forwarded-Prefix");
        if ("/api".equals(forwardedPrefix) && !isApiPath(path)) {
            return forwardedPrefix + (path.startsWith("/") ? path : "/" + path);
        }
        return path;
    }

    private boolean isApiPath(String path) {
        return "/api".equals(path) || path.startsWith("/api/");
    }

    private boolean isPublicApiRequest(String method, String path) {
        return "POST".equalsIgnoreCase(method) && "/api/auth/login".equals(path);
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
}
