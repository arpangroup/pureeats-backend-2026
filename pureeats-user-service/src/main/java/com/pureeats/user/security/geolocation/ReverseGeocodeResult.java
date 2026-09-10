package com.pureeats.user.security.geolocation;

/** Approximate, best-effort address for a coordinate pair - never treat this as a verified/deliverable address the way a saved Address entity is. */
public record ReverseGeocodeResult(
        String displayName,
        String city,
        String state,
        String country,
        String postcode
) {
}
