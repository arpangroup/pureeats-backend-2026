package com.pureeats.app.geodemo.dto;

import java.math.BigDecimal;

/** One ranked result from {@code DistanceCalculator.topNNearest}, paired with its naive ETA. */
public record NearbyRestaurantResult(GeoSimulatorRestaurant restaurant, BigDecimal distanceKm, int etaMinutes) {
}
