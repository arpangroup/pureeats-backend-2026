package com.pureeats.user.security;

import com.pureeats.domain.enums.Role;

/**
 * The JWT principal. Every module downstream of security only ever sees this record
 * (via {@code SecurityContextHolder} / {@code @AuthenticationPrincipal}) - it never
 * needs to depend on pureeats-user-service to know "who is calling and with what role".
 */
public record AuthenticatedUser(
        Long userId,
        String name,
        String email,
        String phone,
        Role role,
        Long deliveryGuyDetailId
) {
}
