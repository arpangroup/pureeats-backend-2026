package com.pureeats.user.security.geolocation;

import java.util.Optional;

/**
 * Resolves an approximate location for an IP. Implementations must never let a slow/unavailable
 * provider fail the caller's request - return {@link Optional#empty()} instead of throwing.
 */
public interface IpGeolocationService {
    Optional<GeoLocation> resolve(String ipAddress);
}
