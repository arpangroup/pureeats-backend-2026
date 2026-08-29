package com.pureeats.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record VerifyResetOtpRequest(@NotBlank @Email String email, @NotBlank String code) {
}
