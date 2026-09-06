package com.pureeats.geo.distance;

/** A cheap rectangular pre-filter around a center point - the same trick {@code RestaurantService.findNearby} already inlines to avoid running the full {@link DistanceCalculator} against every row before ranking. */
public record BoundingBox(String minLat, String minLng, String maxLat, String maxLng) {
}
