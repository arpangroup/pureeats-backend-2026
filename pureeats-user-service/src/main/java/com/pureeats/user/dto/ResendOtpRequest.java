package com.pureeats.user.dto;

import jakarta.validation.constraints.NotBlank;

public record ResendOtpRequest(
        @NotBlank(message = "challengeId is required") String challengeId
) {
}
