package com.pureeats.user.dto;

import java.time.LocalDateTime;

/** Deliberately omits {@code refreshTokenHash} - no reason for even the hash to leave the server. */
public record UserSessionResponse(
        Long id,
        String sessionId,
        Long userId,
        String deviceId,
        LocalDateTime createdAt,
        LocalDateTime expiresAt,
        LocalDateTime lastUsedAt,
        LocalDateTime revokedAt,
        String ipAddress,
        String userAgent
) {
}
