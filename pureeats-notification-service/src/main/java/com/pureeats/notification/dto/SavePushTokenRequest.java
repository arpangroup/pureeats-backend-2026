package com.pureeats.notification.dto;

import jakarta.validation.constraints.NotBlank;

public record SavePushTokenRequest(@NotBlank String token) {
}
