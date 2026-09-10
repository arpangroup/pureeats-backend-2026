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
        boolean cuisineCategorySectionEnabled,
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
        String razorpayKeyId,
        /** Never the raw secret, even for an admin — just whether one has been set, so the admin UI can show "•••• configured" without a plaintext credential ever round-tripping through a browser response. Submitting a blank razorpayKeySecret on save preserves whatever is already stored (see AppConfigService#preserveSecretIfBlank) rather than wiping it. */
        boolean razorpayKeySecretSet,
        String firebaseApiKey,
        String firebaseAuthDomain,
        String firebaseProjectId,
        String firebaseStorageBucket,
        String firebaseMessagingSenderId,
        String firebaseAppId,
        String firebaseVapidKey,
        /** Whether PUT /api/v1/admin/settings and PUT /api/v1/admin/app-config require a confirmation password — computed from whether pureeats.settings.confirmation-password is set (see application.yml), not stored or admin-editable; there's no UI to change it, only that env var. */
        boolean settingsConfirmationEnabled
) {
}
