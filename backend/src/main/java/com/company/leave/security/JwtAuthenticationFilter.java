package com.company.leave.security;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

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
                }
            } catch (RuntimeException ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
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
