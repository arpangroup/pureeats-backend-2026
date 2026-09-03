package com.pureeats.catalog.geo;

import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Great-circle distance over the earth's surface. The default {@link DistanceCalculator} - accurate enough for delivery-radius/pricing decisions without any external API call. */
@Slf4j
public class HaversineDistanceCalculator implements DistanceCalculator {

    private static final double EARTH_RADIUS_KM = 6371.0;

    @Override
    public BigDecimal distanceKm(String lat1, String lng1, String lat2, String lng2) {
        try {
            double distance = haversineKm(Double.parseDouble(lat1), Double.parseDouble(lng1),
                    Double.parseDouble(lat2), Double.parseDouble(lng2));
            return BigDecimal.valueOf(distance).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException | NullPointerException e) {
            log.debug("Could not compute haversine distance from ({}, {}) to ({}, {}), defaulting to zero", lat1, lng1, lat2, lng2);
            return BigDecimal.ZERO;
        }
    }

    private static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }
}
