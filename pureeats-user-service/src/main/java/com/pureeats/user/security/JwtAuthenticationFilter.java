package com.pureeats.user.security;

import com.pureeats.domain.common.CurrentUserContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Validates the bearer JWT (if present) once per request, populates the Spring Security
 * context with an {@link AuthenticatedUser} principal + a single {@code ROLE_*} authority,
 * and mirrors the user id into {@link CurrentUserContext} for modules that don't need a
 * Spring Security dependency to know "who is calling".
 * <p>
 * Registered by pureeats-app's SecurityFilterChain - a missing/invalid token simply leaves
 * the request unauthenticated (the filter chain's authorization rules then reject it).
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    /** Rejects blocked/deactivated users even while their token is still valid (null = no check, e.g. tests). */
    private final AccountAccessGuard accountAccessGuard;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider) {
        this(jwtTokenProvider, null);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        try {
            String token = extractToken(request);
            if (token != null) {
                AuthenticatedUser user = jwtTokenProvider.parseToken(token);
                if (user != null && accountAccessGuard != null) {
                    java.util.Optional<String> denial = accountAccessGuard.denialFor(user.userId());
                    // 401 + these codes: every app signs out (their token refresh fails too - sessions are revoked).
                    if (denial.isPresent()) {
                        reject(response, AccountAccessGuard.ACCOUNT_BLOCKED, denial.get());
                        return;
                    }
                    if (accountAccessGuard.isRevoked(user.userId(), jwtTokenProvider.issuedAt(token))) {
                        reject(response, AccountAccessGuard.SESSION_REVOKED, "You were signed out of all devices. Please sign in again.");
                        return;
                    }
                }
                if (user != null) {
                    var authorities = List.of(new SimpleGrantedAuthority(user.role().authority()));
                    var authentication = new UsernamePasswordAuthenticationToken(user, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    CurrentUserContext.set(user.userId());
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            CurrentUserContext.clear();
        }
    }

    private static void reject(HttpServletResponse response, String errorCode, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"success\":false,\"errorCode\":\"" + errorCode
                + "\",\"message\":\"" + message.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}");
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
