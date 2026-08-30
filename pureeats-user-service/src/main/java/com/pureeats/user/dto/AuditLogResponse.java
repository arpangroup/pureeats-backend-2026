package com.pureeats.user.dto;

import com.pureeats.user.enums.SecurityEventType;

import java.time.LocalDateTime;

public record AuditLogResponse(
        Long id,
        SecurityEventType eventType,
        Long userId,
        String requestId,
        String ipAddress,
        String deviceId,
        String endpoint,
        String httpMethod,
        String result,
        String failureReason,
        String metadata,
        LocalDateTime createdAt
) {
}
