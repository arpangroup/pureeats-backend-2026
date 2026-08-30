package com.pureeats.user.security.metadata;

/** Everything captured about the calling client for a security-relevant request. */
public record RequestMetadata(
        String ipAddress,
        String userAgent,
        String deviceId,
        String deviceType,
        String browser,
        String browserVersion,
        String operatingSystem,
        String osVersion,
        String requestId
) {
}
