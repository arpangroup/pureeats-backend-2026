package com.pureeats.user.dto;

/** A null displayName means resolution failed (disabled, timed out, or the provider had nothing for these coordinates) - callers should fall back to a generic "Current location" label rather than treating this as an error. */
public record ReverseGeocodeResponse(String displayName, String city, String state, String country, String postcode) {
}
