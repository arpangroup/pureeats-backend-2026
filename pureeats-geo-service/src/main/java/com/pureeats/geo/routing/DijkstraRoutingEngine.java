package com.pureeats.geo.routing;

import com.pureeats.geo.distance.DistanceCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Placeholder for a hand-rolled Dijkstra's-algorithm engine over a smaller custom road graph - a
 * lighter alternative to pulling in all of GraphHopper when the service area is small enough that
 * a full OSM extract would be overkill (e.g. one city's road network modeled directly).
 * <p>
 * <b>Not implemented yet</b>: there is no road graph to run Dijkstra over, so every call falls back
 * to a straight-line estimate via {@link DistanceCalculator}, logged once so it's obvious in
 * practice this is a stub. A real implementation would hold an adjacency-list graph (intersections
 * as nodes, road segments as weighted edges) and run standard single-source shortest-path search
 * from the origin node to the destination node.
 */
@Slf4j
@RequiredArgsConstructor
public class DijkstraRoutingEngine implements RoutingEngine {

    private final DistanceCalculator distanceCalculator;

    @Override
    public Route route(String lat1, String lng1, String lat2, String lng2) {
        log.warn("DijkstraRoutingEngine is a placeholder (no road graph loaded) - falling back to a straight-line estimate");
        return new Route(
                distanceCalculator.distanceKm(lat1, lng1, lat2, lng2),
                distanceCalculator.etaMinutes(lat1, lng1, lat2, lng2),
                null);
    }
}
