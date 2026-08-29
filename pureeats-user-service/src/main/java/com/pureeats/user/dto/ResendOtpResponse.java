package com.pureeats.user.dto;

public record ResendOtpResponse(
        boolean success,
        String message,
        long expiresIn,
        long resendAvailableIn
) {
}
