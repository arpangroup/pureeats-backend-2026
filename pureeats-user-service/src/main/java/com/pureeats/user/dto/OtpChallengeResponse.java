package com.pureeats.user.dto;

import com.pureeats.user.enums.AuthenticationMethod;
import com.pureeats.user.enums.OtpChallengeStatus;
import com.pureeats.notification.enums.NotificationType;

import java.time.LocalDateTime;

/** Deliberately omits {@code otpHash}: a bcrypt hash of a 6-digit numeric code is trivially
 * brute-forceable offline, so it must never leave the server, not even to an admin. */
public record OtpChallengeResponse(
        Long id,
        String challengeId,
        Long userId,
        AuthenticationMethod authenticationMethod,
        String maskedDestination,
        NotificationType purpose,
        OtpChallengeStatus status,
        LocalDateTime expiresAt,
        Integer attemptCount,
        Integer maxAttempts,
        Integer resendCount,
        Integer maxResendCount,
        LocalDateTime lastSentAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime verifiedAt,
        String ipAddress,
        String deviceId,
        String requestId
) {
}
