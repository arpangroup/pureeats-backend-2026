package com.pureeats.catalog.geo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * Real road-distance via Google's Distance Matrix API. Same stub-until-configured shape as
 * {@code FcmSender}: with no API key set, every call falls back to {@link HaversineDistanceCalculator}
 * rather than throwing, so selecting this implementation is always safe even before ops provisions a key.
 */
@Slf4j
public class GoogleDistanceMatrixCalculator implements DistanceCalculator {

    private final RestClient restClient;
    private final String apiKey;
    private final HaversineDistanceCalculator fallback = new HaversineDistanceCalculator();

    public GoogleDistanceMatrixCalculator(String apiKey, int timeoutMs) {
        this.apiKey = apiKey;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);
        this.restClient = RestClient.builder()
                .baseUrl("https://maps.googleapis.com")
                .requestFactory(factory)
                .build();
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Google Distance Matrix selected as distance provider but no API key configured (pureeats.distance.google.api-key) - falling back to haversine for every request");
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public BigDecimal distanceKm(String lat1, String lng1, String lat2, String lng2) {
        if (apiKey == null || apiKey.isBlank() || lat1 == null || lng1 == null || lat2 == null || lng2 == null) {
            return fallback.distanceKm(lat1, lng1, lat2, lng2);
        }
        try {
            Map<String, Object> body = restClient.get()
                    .uri("/maps/api/distancematrix/json?origins={o}&destinations={d}&key={key}",
                            lat1 + "," + lng1, lat2 + "," + lng2, apiKey)
                    .retrieve()
                    .body(Map.class);
            if (body == null || !"OK".equals(body.get("status"))) {
                return fallback.distanceKm(lat1, lng1, lat2, lng2);
            }
            List<Map<String, Object>> rows = (List<Map<String, Object>>) body.get("rows");
            Map<String, Object> element = (Map<String, Object>) ((List<Map<String, Object>>) rows.get(0).get("elements")).get(0);
            if (!"OK".equals(element.get("status"))) {
                return fallback.distanceKm(lat1, lng1, lat2, lng2);
            }
            Map<String, Object> distance = (Map<String, Object>) element.get("distance");
            double meters = ((Number) distance.get("value")).doubleValue();
            return BigDecimal.valueOf(meters / 1000.0).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            log.warn("Google Distance Matrix lookup failed, falling back to haversine: {}", e.getMessage());
            return fallback.distanceKm(lat1, lng1, lat2, lng2);
        }
    }
}
