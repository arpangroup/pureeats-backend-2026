package com.pureeats.catalog.geo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Selects the single active {@link DistanceCalculator} implementation via
 * {@code pureeats.distance.provider} (haversine / euclidean / google) - the exact same
 * {@code @ConditionalOnProperty}-per-bean pattern {@code NotificationProviderConfig} uses to pick
 * the active email/SMS provider. Adding a new algorithm (e.g. a routed-road-distance service) means
 * writing one more {@link DistanceCalculator} implementation and one more {@code @Bean} method here
 * - every existing caller (delivery-charge pricing, delivery-area checks, nearby-restaurant search)
 * keeps injecting the plain interface and never changes.
 */
@Configuration
public class DistanceCalculatorConfig {

    @Bean
    @ConditionalOnProperty(prefix = "pureeats.distance", name = "provider", havingValue = "haversine", matchIfMissing = true)
    public DistanceCalculator haversineDistanceCalculator() {
        return new HaversineDistanceCalculator();
    }

    @Bean
    @ConditionalOnProperty(prefix = "pureeats.distance", name = "provider", havingValue = "euclidean")
    public DistanceCalculator euclideanDistanceCalculator() {
        return new EuclideanDistanceCalculator();
    }

    @Bean
    @ConditionalOnProperty(prefix = "pureeats.distance", name = "provider", havingValue = "google")
    public DistanceCalculator googleDistanceMatrixCalculator(
            @Value("${pureeats.distance.google.api-key:}") String apiKey,
            @Value("${pureeats.distance.google.timeout-ms:3000}") int timeoutMs) {
        return new GoogleDistanceMatrixCalculator(apiKey, timeoutMs);
    }
}
