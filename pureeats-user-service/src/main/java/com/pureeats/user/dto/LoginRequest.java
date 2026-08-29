package com.pureeats.user.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "Email or phone is required") String emailOrPhone,
        @NotBlank String password
) {
}
