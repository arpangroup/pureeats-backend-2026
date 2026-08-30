package com.pureeats.user.security.geolocation;

/** Approximate, best-effort location for an IP address - never treat this as an exact physical location. */
public record GeoLocation(
        String country,
        String countryCode,
        String region,
        String city,
        Double latitude,
        Double longitude,
        String timezone,
        String isp
) {
}
