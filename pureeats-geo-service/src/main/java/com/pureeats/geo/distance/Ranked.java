package com.pureeats.geo.distance;

import java.math.BigDecimal;

/** One candidate from {@link DistanceCalculator#topNNearest}, paired with its computed distance from the query origin. */
public record Ranked<T>(T item, BigDecimal distanceKm) {
}
