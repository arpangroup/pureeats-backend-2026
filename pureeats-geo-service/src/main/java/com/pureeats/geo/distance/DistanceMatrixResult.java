package com.pureeats.geo.distance;

import com.pureeats.geo.LatLng;

import java.math.BigDecimal;
import java.util.List;

/** {@code distancesKm[i][j]} is the distance from {@code origins.get(i)} to {@code destinations.get(j)}, matching Google's Distance Matrix API response shape. */
public record DistanceMatrixResult(List<LatLng> origins, List<LatLng> destinations, BigDecimal[][] distancesKm) {
}
