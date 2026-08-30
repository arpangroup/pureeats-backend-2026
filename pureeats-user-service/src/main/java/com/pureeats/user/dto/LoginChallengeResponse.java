package com.pureeats.user.dto;

public record LoginChallengeResponse(
        boolean success,
        String message,
        String challengeId,
        String maskedDestination,
        long expiresIn,
        long resendAvailableIn
) {
}
