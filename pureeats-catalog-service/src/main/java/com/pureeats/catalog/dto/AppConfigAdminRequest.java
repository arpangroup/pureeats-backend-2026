package com.pureeats.catalog.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record AppConfigAdminRequest(
        @NotBlank String latestVersion,
        @NotBlank String minSupportedVersion,
        String message,
        String googleMapsApiKey,
        List<String> enabledPaymentMethods,
        boolean forceLogoutOnHardUpdate
) {
}
