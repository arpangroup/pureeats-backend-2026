package com.pureeats.geo;

/**
 * A coordinate pair, kept as {@code String} (not {@code double}) to match {@link DistanceCalculator}'s
 * existing signature and the way every caller already holds coordinates - {@code Restaurant}/{@code Address}
 * store latitude/longitude as text, so this avoids a parse/format round-trip at every call site.
 */
public record LatLng(String lat, String lng) {
}
