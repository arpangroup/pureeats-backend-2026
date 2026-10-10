package com.pureeats.geo.distance;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The application's single {@link DistanceCalculator}: a {@link SwitchableDistanceCalculator} that picks
 * straight-line or Google Distance Matrix on every call from {@link DistanceSettings} (admin Settings -> General ->
 * Distance &amp; travel time). Every caller (delivery-charge pricing, delivery-area checks, restaurant lists, ETAs,
 * the partner's distances) keeps injecting the plain interface and never changes.
 * <p>
 * The {@code pureeats.distance.*} properties are the fallback when the admin hasn't chosen yet (or no
 * {@link DistanceSettings} bean exists): {@code provider=google} + {@code google.api-key} still turns Google on, and
 * {@code provider=euclidean} still selects the flat-plane approximation as the straight-line method.
 */
@Configuration
public class DistanceCalculatorConfig {

    @Bean
    public DistanceCalculator distanceCalculator(
            ObjectProvider<DistanceSettings> settingsProvider,
            @Value("${pureeats.distance.provider:haversine}") String provider,
            @Value("${pureeats.distance.google.api-key:}") String apiKey,
            @Value("${pureeats.distance.google.timeout-ms:3000}") int timeoutMs) {
        DistanceSettings fromProperties = propertySettings(provider, apiKey);
        DistanceCalculator straightLine = "euclidean".equalsIgnoreCase(provider)
                ? new EuclideanDistanceCalculator() : new HaversineDistanceCalculator();
        // Resolved lazily on each call - the settings bean lives in another module and is created after this one.
        return new SwitchableDistanceCalculator(() -> settingsProvider.getIfAvailable(() -> fromProperties), straightLine, timeoutMs);
    }

    static DistanceSettings propertySettings(String provider, String apiKey) {
        return new DistanceSettings() {
            @Override
            public String method() {
                return "google".equalsIgnoreCase(provider) ? GOOGLE : STRAIGHT_LINE;
            }

            @Override
            public String googleApiKey() {
                return apiKey;
            }
        };
    }
}
