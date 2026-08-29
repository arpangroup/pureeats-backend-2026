package com.pureeats.user.dto;

import java.time.LocalDateTime;

public record UserDeviceResponse(
        Long id,
        Long userId,
        String deviceId,
        String deviceType,
        String browser,
        String browserVersion,
        String operatingSystem,
        String osVersion,
        String ipAddress,
        LocalDateTime firstSeenAt,
        LocalDateTime lastSeenAt,
        LocalDateTime lastLoginAt
) {
}
