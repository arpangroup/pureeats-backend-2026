package com.pureeats.geo.routing;

import com.pureeats.geo.distance.DistanceCalculator;
import lombok.RequiredArgsConstructor;

/**
 * The only implementation here that isn't a placeholder - wraps whichever
 * {@link DistanceCalculator} is active and reuses its {@code etaMinutes} default. This is the
 * {@code matchIfMissing} default in {@link RoutingEngineConfig}, so requesting a {@code RoutingEngine}
 * bean always works even before a real road-graph engine exists - callers get an honest
 * straight-line approximation (no {@code polyline}, since there's no traced path) rather than
 * an exception.
 */
@RequiredArgsConstructor
public class StraightLineRoutingEngine implements RoutingEngine {

    private final DistanceCalculator distanceCalculator;

    @Override
    public Route route(String lat1, String lng1, String lat2, String lng2) {
        return new Route(
                distanceCalculator.distanceKm(lat1, lng1, lat2, lng2),
                distanceCalculator.etaMinutes(lat1, lng1, lat2, lng2),
                null);
    }
}
