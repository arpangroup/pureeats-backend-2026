package com.pureeats.user.dto;

/** Null lat/lng means resolution failed (private/loopback IP in dev, geolocation provider unavailable, ...) - callers should fall back to a default map center rather than treating this as an error. */
public record IpLocationResponse(Double latitude, Double longitude, String city, String country) {
}
