package com.pureeats.user.dto;

import com.pureeats.user.enums.LoginMethod;

import java.time.LocalDateTime;

public record LoginHistoryResponse(
        Long id,
        Long userId,
        LoginMethod loginMethod,
        String status,
        String ipAddress,
        String deviceId,
        String userAgent,
        String country,
        String region,
        String city,
        Double latitude,
        Double longitude,
        LocalDateTime occurredAt,
        String failureReason
) {
}
