package com.pureeats.geo.routing;

import com.pureeats.geo.distance.DistanceCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Placeholder for an A*-search engine over the same kind of road graph {@link DijkstraRoutingEngine}
 * would use - A* adds a straight-line-distance heuristic (which {@link DistanceCalculator} already
 * gives us for free) to guide the search toward the destination, visiting far fewer nodes than
 * plain Dijkstra for a single point-to-point query. Worth it once real-time queries against a large
 * graph are common; Dijkstra alone is simpler and fine for occasional/batch use.
 * <p>
 * <b>Not implemented yet</b>: same story as {@link DijkstraRoutingEngine} - no road graph exists
 * yet, so every call falls back to a straight-line estimate, logged once so it's obvious in
 * practice this is a stub.
 */
@Slf4j
@RequiredArgsConstructor
public class AStarRoutingEngine implements RoutingEngine {

    private final DistanceCalculator distanceCalculator;

    @Override
    public Route route(String lat1, String lng1, String lat2, String lng2) {
        log.warn("AStarRoutingEngine is a placeholder (no road graph loaded) - falling back to a straight-line estimate");
        return new Route(
                distanceCalculator.distanceKm(lat1, lng1, lat2, lng2),
                distanceCalculator.etaMinutes(lat1, lng1, lat2, lng2),
                null);
    }
}
