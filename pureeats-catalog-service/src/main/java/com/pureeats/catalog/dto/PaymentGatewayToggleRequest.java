package com.pureeats.catalog.dto;

import jakarta.validation.constraints.NotNull;

/** {@code confirmationPassword} is only checked when AppConfig's settingsConfirmationEnabled is on - see AppConfigService#verifyConfirmationPassword, same gate every other settings write goes through. */
public record PaymentGatewayToggleRequest(@NotNull Boolean isActive, String confirmationPassword) {
}
