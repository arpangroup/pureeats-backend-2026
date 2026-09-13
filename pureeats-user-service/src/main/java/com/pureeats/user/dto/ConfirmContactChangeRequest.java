package com.pureeats.user.dto;

import jakarta.validation.constraints.NotBlank;

public record ConfirmContactChangeRequest(
        @NotBlank String challengeId,
        @NotBlank String otp
) {
}
