package com.pureeats.geo.polygon;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * This is the feature flag: unlike {@code DistanceCalculatorConfig}/{@code RoutingEngineConfig}
 * (which always register exactly one bean, just picking which implementation), a
 * {@link PolygonBoundaryService} bean is only created at all when
 * {@code pureeats.geo.polygon-boundary.enabled=true}. No property (or {@code false}) means no bean
 * in the Spring context - {@code @Autowired PolygonBoundaryService} would fail to start up, so any
 * consumer must either depend on {@code Optional<PolygonBoundaryService>} or be
 * {@code @ConditionalOnBean(PolygonBoundaryService.class)} itself, the same way a proposed
 * {@code DeliveryPolygonRule} in {@code pureeats-order-service} would be. Flipping this one
 * property on/off is the entire "apply this rule or ignore it" switch described in
 * {@code architecture-map.html} - no other code changes.
 */
@Configuration
public class PolygonBoundaryServiceConfig {

    @Bean
    @ConditionalOnProperty(prefix = "pureeats.geo.polygon-boundary", name = "enabled", havingValue = "true")
    public PolygonBoundaryService polygonBoundaryService() {
        return new InMemoryPolygonBoundaryService();
    }
}
