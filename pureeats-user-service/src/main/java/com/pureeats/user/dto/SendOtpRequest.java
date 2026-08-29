package com.pureeats.user.dto;

import jakarta.validation.constraints.NotBlank;

public record SendOtpRequest(@NotBlank String phone) {
}
