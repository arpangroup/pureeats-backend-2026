package com.pureeats.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Email-based signup + verification. Phone-only sign-up is handled by {@code /auth/otp/initiate} (auto-provisions on first verify), so there is no separate phone signup shape here. */
public record SignupRequest(
        @NotBlank(message = "fullName is required") String fullName,
        @NotBlank(message = "email is required") @Email(message = "email must be valid") String email
) {
}
