package com.pureeats.catalog.dto;

import java.util.List;

/** The raw stored config, as the admin panel edits it - contrast with AppConfigResponse, which is what the app itself fetches (computed severity, no version numbers). */
public record AppConfigAdminResponse(
        String latestVersion,
        String minSupportedVersion,
        String message,
        String googleMapsApiKey,
        List<String> enabledPaymentMethods,
        boolean forceLogoutOnHardUpdate,
        boolean audioSearchEnabled,
        boolean promoSliderEnabled,
        boolean topPicksEnabled,
        boolean recommendedItemsEnabled,
        String restaurantListLayout,
        String recommendedItemsLayout,
        String restaurantItemsLayout,
        String deliveryInstructionMode,
        List<DeliveryInstructionOptionDto> deliveryInstructionOptions,
        String mapProvider,
        String orderStatusUpdateMode,
        int orderStatusPollIntervalMs
) {
}
