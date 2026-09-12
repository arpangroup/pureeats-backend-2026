package com.pureeats.catalog.dto;

import jakarta.validation.constraints.NotNull;

public record PaymentGatewayToggleRequest(@NotNull Boolean isActive) {
}
