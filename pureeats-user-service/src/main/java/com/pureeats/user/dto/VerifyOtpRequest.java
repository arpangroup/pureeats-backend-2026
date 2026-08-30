package com.pureeats.user.dto;

import jakarta.validation.constraints.NotBlank;

public record VerifyOtpRequest(
        @NotBlank(message = "challengeId is required") String challengeId,
        @NotBlank(message = "otp is required") String otp
) {
}
