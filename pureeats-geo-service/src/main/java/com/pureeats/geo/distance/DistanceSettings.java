package com.pureeats.geo.distance;

/**
 * Which distance method is active right now, read on every call so an admin can switch it without a restart.
 * Implemented by the admin-settings layer (catalog-service); when no implementation is present the
 * {@code pureeats.distance.*} properties are used (see {@link DistanceCalculatorConfig}).
 */
public interface DistanceSettings {

    /** Straight-line (haversine) distance - the default. */
    String STRAIGHT_LINE = "STRAIGHT_LINE";
    /** Real road distance and driving time from the Google Distance Matrix API. */
    String GOOGLE = "GOOGLE_DISTANCE_MATRIX";

    /** {@link #STRAIGHT_LINE} or {@link #GOOGLE}. */
    String method();

    /** Server-side Google Maps key with the Distance Matrix API enabled; blank = Google can't be used. */
    String googleApiKey();
}
