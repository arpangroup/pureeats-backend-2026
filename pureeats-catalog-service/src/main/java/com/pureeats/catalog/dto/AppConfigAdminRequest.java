package com.pureeats.catalog.dto;

import java.util.List;

/**
 * Every field here is nullable and PUT /api/v1/admin/app-config treats a null as "leave this one
 * alone" — see AppConfigService#mergeOntoExisting. That's what lets the admin panel send just the
 * one or two fields a customer actually edited (a Google Maps key, say) instead of round-tripping
 * every other field's current value along with it. latestVersion/minSupportedVersion used to be
 * {@code @NotBlank} (required on every write); that's exactly what a partial-update contract can't
 * have, so it's gone — a write that omits them simply leaves whatever's already stored.
 */
public record AppConfigAdminRequest(
        String latestVersion,
        String minSupportedVersion,
        String message,
        String googleMapsApiKey,
        List<String> enabledPaymentMethods,
        Boolean forceLogoutOnHardUpdate,
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
        String locationResolutionGuestFallbackLabel,
        String razorpayKeyId,
        /** Write-only from the admin UI's perspective — never returned by getForAdmin()/forClient(). A blank/null value here on update() preserves whatever secret is already stored rather than erasing it; see AppConfigService#preserveSecretIfBlank. */
        String razorpayKeySecret,
        String firebaseApiKey,
        String firebaseAuthDomain,
        String firebaseProjectId,
        String firebaseStorageBucket,
        String firebaseMessagingSenderId,
        String firebaseAppId,
        String firebaseVapidKey
) {
}
