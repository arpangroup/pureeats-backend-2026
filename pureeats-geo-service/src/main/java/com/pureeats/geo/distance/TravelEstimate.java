package com.pureeats.geo.distance;

import java.math.BigDecimal;

/** Distance (km) and travel time (minutes) between two points - one Google Distance Matrix element, or the straight-line estimate. */
public record TravelEstimate(BigDecimal distanceKm, int minutes) {

    public static final TravelEstimate ZERO = new TravelEstimate(BigDecimal.ZERO, 0);
}
