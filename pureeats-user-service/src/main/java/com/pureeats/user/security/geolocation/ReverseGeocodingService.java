package com.pureeats.user.security.geolocation;

import java.util.Optional;

/**
 * Resolves a readable address for a lat/lon pair. Implementations must never let a slow/unavailable
 * provider fail the caller's request - return {@link Optional#empty()} instead of throwing.
 */
public interface ReverseGeocodingService {
    Optional<ReverseGeocodeResult> resolve(double latitude, double longitude);
}
