package com.pureeats.catalog.geo;

import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Flat-plane (Pythagorean) approximation, converting the lat/lng degree delta to kilometers via a
 * fixed 111.32 km/degree scale. Cheaper than {@link HaversineDistanceCalculator} but only accurate
 * over short distances away from the poles - kept as a demonstration of swapping the distance
 * algorithm purely via config (see {@link DistanceCalculatorConfig}), not because it should be the
 * default.
 */
@Slf4j
public class EuclideanDistanceCalculator implements DistanceCalculator {

    private static final double KM_PER_DEGREE = 111.32;

    @Override
    public BigDecimal distanceKm(String lat1, String lng1, String lat2, String lng2) {
        try {
            double a1 = Double.parseDouble(lat1);
            double o1 = Double.parseDouble(lng1);
            double a2 = Double.parseDouble(lat2);
            double o2 = Double.parseDouble(lng2);
            double avgLatRad = Math.toRadians((a1 + a2) / 2.0);
            double dLatKm = (a2 - a1) * KM_PER_DEGREE;
            double dLngKm = (o2 - o1) * KM_PER_DEGREE * Math.cos(avgLatRad);
            double distance = Math.sqrt(dLatKm * dLatKm + dLngKm * dLngKm);
            return BigDecimal.valueOf(distance).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException | NullPointerException e) {
            log.debug("Could not compute euclidean distance from ({}, {}) to ({}, {}), defaulting to zero", lat1, lng1, lat2, lng2);
            return BigDecimal.ZERO;
        }
    }
}
