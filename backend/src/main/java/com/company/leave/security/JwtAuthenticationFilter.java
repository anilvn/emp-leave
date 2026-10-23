package com.company.leave.security;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import io.jsonwebtoken.JwtException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String LOGOUT_PATH = "/auth/logout";

    private final JwtService jwtService;
    private final SessionRegistry sessionRegistry;

    JwtAuthenticationFilter(JwtService jwtService, SessionRegistry sessionRegistry) {
        this.jwtService = jwtService;
        this.sessionRegistry = sessionRegistry;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = accessToken(request);
        if (token != null) {
            try {
                JwtService.ParsedToken parsed = jwtService.parseAndValidate(token);
                if (sessionRegistry.touchOrReject(parsed.tokenId())) {
                    var authentication = new UsernamePasswordAuthenticationToken(
                            parsed.userId(),
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + parsed.role())));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    slideCookie(request, response, parsed);
                }
            } catch (JwtException ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }

    /**
     * Re-issues a fresh token cookie so the 30-minute window tracks activity rather than login
     * time (FR58, AD-8). The same {@code token_id} is reused, keeping the session row stable.
     * Logout is skipped so its clearing cookie is the only one returned.
     */
    private void slideCookie(
            HttpServletRequest request, HttpServletResponse response, JwtService.ParsedToken parsed) {
        if (LOGOUT_PATH.equals(requestPath(request))) {
            return;
        }
        String refreshed = jwtService.issue(parsed.userId(), parsed.role(), parsed.tokenId());
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                AuthCookies.issue(refreshed, JwtService.TOKEN_LIFETIME).toString());
    }

    private String requestPath(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        return contextPath != null && !contextPath.isEmpty() && requestUri.startsWith(contextPath)
                ? requestUri.substring(contextPath.length())
                : requestUri;
    }

    private String accessToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if ("access_token".equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
