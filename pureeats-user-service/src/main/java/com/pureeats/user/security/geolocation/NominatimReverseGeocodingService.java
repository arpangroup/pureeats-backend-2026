package com.pureeats.user.security.geolocation;

import com.pureeats.user.config.AuthSecurityProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Calls the free OpenStreetMap Nominatim JSON reverse-geocoding endpoint (no API key, best-effort
 * and rate-limited to ~1 req/sec per their usage policy - fine for a location *hint*, never a
 * verified deliverable address). Swap to a paid provider later by adding a sibling
 * {@link ReverseGeocodingService} implementation and pointing
 * {@code security.reverse-geocoding.provider} at it - nothing else changes since callers only ever
 * see the interface.
 *
 * <p>Routing this through the backend rather than having the customer app call Nominatim directly
 * keeps the required {@code User-Agent} header, request caching, and provider choice all in one
 * place - see docs/location-resolution/README.md for the full rationale.
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "security.reverse-geocoding", name = "provider", havingValue = "nominatim", matchIfMissing = true)
public class NominatimReverseGeocodingService implements ReverseGeocodingService {

    private final RestClient restClient;
    private final AuthSecurityProperties properties;
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public NominatimReverseGeocodingService(AuthSecurityProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getReverseGeocoding().getTimeoutMs());
        factory.setReadTimeout(properties.getReverseGeocoding().getTimeoutMs());
        this.restClient = RestClient.builder()
                .baseUrl("https://nominatim.openstreetmap.org")
                .requestFactory(factory)
                // Required by Nominatim's usage policy (https://operations.osmfoundation.org/policies/nominatim/)
                // for any application making automated requests - a plain browser fetch can't set this, which is
                // one of the reasons this call lives server-side instead of in the customer app.
                .defaultHeader("User-Agent", "PureEats-Backend/1.0 (reverse-geocoding proxy)")
                .build();
    }

    @Override
    public Optional<ReverseGeocodeResult> resolve(double latitude, double longitude) {
        if (!properties.getReverseGeocoding().isEnabled()) {
            log.debug("Skipping reverse geocode lookup (disabled)");
            return Optional.empty();
        }

        String key = cacheKey(latitude, longitude);
        CacheEntry cached = cache.get(key);
        if (cached != null && cached.isFresh(properties.getReverseGeocoding().getCacheTtlMinutes())) {
            log.debug("Reverse geocode cache hit");
            return cached.value();
        }

        Optional<ReverseGeocodeResult> resolved = fetch(latitude, longitude);
        log.debug("Reverse geocode lookup resolved={}", resolved.isPresent());
        cache.put(key, new CacheEntry(resolved, Instant.now()));
        return resolved;
    }

    /**
     * Rounded to 4 decimal places (~11m of precision) - plenty for a "which street are you on"
     * label, and it turns nearby repeat lookups (a customer's phone drifting a few meters, or many
     * customers in the same neighbourhood) into cache hits instead of hammering Nominatim's shared
     * public instance.
     */
    private String cacheKey(double latitude, double longitude) {
        return String.format(Locale.ROOT, "%.4f,%.4f", latitude, longitude);
    }

    @SuppressWarnings("unchecked")
    private Optional<ReverseGeocodeResult> fetch(double latitude, double longitude) {
        try {
            Map<String, Object> body = restClient.get()
                    .uri("/reverse?format=jsonv2&lat={lat}&lon={lon}", latitude, longitude)
                    .retrieve()
                    .body(Map.class);

            if (body == null || body.get("display_name") == null) {
                return Optional.empty();
            }
            Map<String, Object> address = body.get("address") instanceof Map ? (Map<String, Object>) body.get("address") : Map.of();
            String city = firstNonBlank((String) address.get("city"), (String) address.get("town"),
                    (String) address.get("village"), (String) address.get("suburb"));
            return Optional.of(new ReverseGeocodeResult(
                    (String) body.get("display_name"),
                    city,
                    (String) address.get("state"),
                    (String) address.get("country"),
                    (String) address.get("postcode")
            ));
        } catch (Exception e) {
            log.debug("Reverse geocode lookup failed (non-fatal): {}", e.getMessage());
            return Optional.empty();
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private record CacheEntry(Optional<ReverseGeocodeResult> value, Instant cachedAt) {
        boolean isFresh(int ttlMinutes) {
            return cachedAt.plus(Duration.ofMinutes(ttlMinutes)).isAfter(Instant.now());
        }
    }
}
