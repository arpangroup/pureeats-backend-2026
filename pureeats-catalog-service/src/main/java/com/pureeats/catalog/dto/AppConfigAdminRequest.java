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
        Integer orderStatusPollIntervalMs,
        /** Ordered "saved" | "gps" | "ip" list — the first source with a resolved value wins the home page's active-address label. See customer app's src/config/locationResolution.ts for the matching client-side fallback and docs/location-resolution/README.md for the full design. */
        List<String> locationResolutionAuthenticatedPriority,
        /** Same as above, for signed-out customers — 'saved' is meaningless here since a guest has no saved address, but nothing stops an admin from including it (it'll simply never resolve). */
        List<String> locationResolutionGuestPriority,
        String locationResolutionAuthenticatedFallbackLabel,
        String locationResolutionGuestFallbackLabel
) {
}
