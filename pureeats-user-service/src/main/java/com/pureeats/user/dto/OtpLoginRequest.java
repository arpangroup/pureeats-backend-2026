package com.pureeats.user.dto;

import jakarta.validation.constraints.NotBlank;

public record OtpLoginRequest(
        @NotBlank String phone,
        @NotBlank String otp,
        String name
) {
}
