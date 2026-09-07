package com.pureeats.geo.routing;

import com.pureeats.geo.distance.DistanceCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Placeholder for a real <a href="https://www.graphhopper.com/">GraphHopper</a>-backed engine -
 * self-hosted routing over a precomputed road graph built from an OpenStreetMap extract, using
 * Contraction Hierarchies (or plain Dijkstra/A*) for fast point-to-point queries. No per-call cost
 * or rate limit, unlike {@code GoogleDistanceMatrixCalculator}, but needs map-data hosting and a
 * meaningful amount of memory to hold the graph.
 * <p>
 * <b>Not implemented yet</b>: {@code osmExtractPath} is accepted (and would be handed to
 * GraphHopper's own graph-loading API) but never read - every call falls back to a straight-line
 * estimate via {@link DistanceCalculator}, logged once so it's obvious in practice this is a stub.
 * Swapping this for the real thing is a matter of loading the GraphHopper library, building/loading
 * its graph cache from {@code osmExtractPath} at startup, and replacing the body of {@link #route}
 * with an actual {@code GraphHopper.route(...)} call - nothing about the interface or its callers
 * changes.
 */
@Slf4j
@RequiredArgsConstructor
public class GraphHopperRoutingEngine implements RoutingEngine {

    private final DistanceCalculator distanceCalculator;
    private final String osmExtractPath;

    @Override
    public Route route(String lat1, String lng1, String lat2, String lng2) {
        log.warn("GraphHopperRoutingEngine is a placeholder (osmExtractPath='{}' not yet loaded) - " +
                "falling back to a straight-line estimate", osmExtractPath);
        return new Route(
                distanceCalculator.distanceKm(lat1, lng1, lat2, lng2),
                distanceCalculator.etaMinutes(lat1, lng1, lat2, lng2),
                null);
    }
}
