package com.pureeats.catalog.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record AppConfigAdminRequest(
        @NotBlank String latestVersion,
        @NotBlank String minSupportedVersion,
        String message,
        String googleMapsApiKey,
        List<String> enabledPaymentMethods,
        boolean forceLogoutOnHardUpdate,
        Boolean audioSearchEnabled,
        Boolean promoSliderEnabled,
        Boolean topPicksEnabled,
        Boolean recommendedItemsEnabled,
        String restaurantListLayout,
        String recommendedItemsLayout,
        String restaurantItemsLayout,
        String deliveryInstructionMode,
        List<DeliveryInstructionOptionDto> deliveryInstructionOptions,
        String mapProvider,
        String orderStatusUpdateMode,
        Integer orderStatusPollIntervalMs
) {
}
