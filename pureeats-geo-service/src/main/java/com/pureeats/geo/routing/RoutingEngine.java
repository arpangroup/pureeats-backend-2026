package com.pureeats.geo.routing;

import com.pureeats.geo.distance.DistanceCalculator;

/**
 * Real road-network routing, as opposed to {@link DistanceCalculator}'s straight
 * line - a separate interface (not a {@code DistanceCalculator} default method) because a route
 * needs a road graph to traverse, which none of today's {@code DistanceCalculator} implementations
 * have. {@code @ConditionalOnProperty}-selected in {@link RoutingEngineConfig}, the same pattern
 * {@code DistanceCalculatorConfig} already uses.
 * <p>
 * Every implementation here today is a placeholder - none of them have real road-graph data yet
 * (see each class's javadoc for exactly what's missing). They exist so the interface, the
 * config-selection pattern, and every future call site can be written and wired up now, and made
 * real later by swapping one class's internals - never the interface or its callers.
 */
public interface RoutingEngine {
    Route route(String lat1, String lng1, String lat2, String lng2);
}
