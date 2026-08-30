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

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        try {
            String token = extractToken(request);
            if (token != null) {
                AuthenticatedUser user = jwtTokenProvider.parseToken(token);
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

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
