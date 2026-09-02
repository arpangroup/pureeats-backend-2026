package com.pureeats.catalog.dto;

import java.util.List;

/** What the app itself fetches at boot - severity is computed server-side from the client's own version, never trusted from the client. */
public record AppConfigResponse(
        String severity,
        String message,
        String latestVersion,
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
