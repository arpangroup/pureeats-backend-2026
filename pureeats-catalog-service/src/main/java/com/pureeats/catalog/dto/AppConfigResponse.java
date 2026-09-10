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
        int orderStatusPollIntervalMs,
        List<String> locationResolutionAuthenticatedPriority,
        List<String> locationResolutionGuestPriority,
        String locationResolutionAuthenticatedFallbackLabel,
        String locationResolutionGuestFallbackLabel,
        /** Public by design — a Razorpay Key ID (unlike the secret) is meant to be embedded client-side; Razorpay's own checkout widget requires it in the browser. */
        String razorpayKeyId,
        /** Firebase web config is designed to be public (Firebase protects data via server-side Security Rules, not by hiding these values) — safe to serve from the same public endpoint as everything else here. */
        String firebaseApiKey,
        String firebaseAuthDomain,
        String firebaseProjectId,
        String firebaseStorageBucket,
        String firebaseMessagingSenderId,
        String firebaseAppId,
        String firebaseVapidKey
) {
}
