package com.pureeats.geo.routing;

import com.pureeats.geo.distance.DistanceCalculator;
import com.pureeats.geo.distance.DistanceCalculatorConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Selects the active {@link RoutingEngine} via {@code pureeats.routing.provider} - the exact same
 * {@code @ConditionalOnProperty}-per-bean pattern {@link DistanceCalculatorConfig}
 * already uses. Defaults to {@link StraightLineRoutingEngine} (the only non-placeholder
 * implementation) so a {@code RoutingEngine} bean always exists, even before a real road-graph
 * engine is built.
 */
@Configuration
public class RoutingEngineConfig {

    @Bean
    @ConditionalOnProperty(prefix = "pureeats.routing", name = "provider", havingValue = "straight-line", matchIfMissing = true)
    public RoutingEngine straightLineRoutingEngine(DistanceCalculator distanceCalculator) {
        return new StraightLineRoutingEngine(distanceCalculator);
    }

    @Bean
    @ConditionalOnProperty(prefix = "pureeats.routing", name = "provider", havingValue = "graphhopper")
    public RoutingEngine graphHopperRoutingEngine(DistanceCalculator distanceCalculator,
                                                   @Value("${pureeats.routing.graphhopper.osm-extract-path:}") String osmExtractPath) {
        return new GraphHopperRoutingEngine(distanceCalculator, osmExtractPath);
    }

    @Bean
    @ConditionalOnProperty(prefix = "pureeats.routing", name = "provider", havingValue = "dijkstra")
    public RoutingEngine dijkstraRoutingEngine(DistanceCalculator distanceCalculator) {
        return new DijkstraRoutingEngine(distanceCalculator);
    }

    @Bean
    @ConditionalOnProperty(prefix = "pureeats.routing", name = "provider", havingValue = "astar")
    public RoutingEngine aStarRoutingEngine(DistanceCalculator distanceCalculator) {
        return new AStarRoutingEngine(distanceCalculator);
    }
}
