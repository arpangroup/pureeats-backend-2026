package com.pureeats.catalog.dto;

import java.util.List;

/** What the app itself fetches at boot - severity is computed server-side from the client's own version, never trusted from the client. */
public record AppConfigResponse(
        String severity,
        String message,
        String latestVersion,
        String googleMapsApiKey,
        List<String> enabledPaymentMethods,
        boolean forceLogoutOnHardUpdate
) {
}
