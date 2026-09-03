package com.pureeats.user.security.geolocation;

import com.pureeats.user.config.AuthSecurityProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Calls the free ip-api.com JSON endpoint (no API key, best-effort/rate-limited - fine for a
 * "nice to have" on login history, not a security control). Swap to a paid service later by
 * adding a sibling {@link IpGeolocationService} implementation and pointing
 * {@code pureeats.security.geolocation.provider} at it - nothing else changes since callers only
 * ever see the interface.
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "security.geolocation", name = "provider", havingValue = "ip-api", matchIfMissing = true)
public class HttpIpGeolocationService implements IpGeolocationService {

    private final RestClient restClient;
    private final AuthSecurityProperties properties;
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public HttpIpGeolocationService(AuthSecurityProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getGeolocation().getTimeoutMs());
        factory.setReadTimeout(properties.getGeolocation().getTimeoutMs());
        this.restClient = RestClient.builder()
                .baseUrl("http://ip-api.com")
                .requestFactory(factory)
                .build();
    }

    @Override
    public Optional<GeoLocation> resolve(String ipAddress) {
        if (!properties.getGeolocation().isEnabled() || isPrivateOrLoopback(ipAddress)) {
            log.debug("Skipping IP geolocation lookup (disabled or private/loopback address)");
            return Optional.empty();
        }

        CacheEntry cached = cache.get(ipAddress);
        if (cached != null && cached.isFresh(properties.getGeolocation().getCacheTtlMinutes())) {
            log.debug("IP geolocation cache hit");
            return cached.value();
        }

        Optional<GeoLocation> resolved = fetch(ipAddress);
        log.debug("IP geolocation lookup resolved={}", resolved.isPresent());
        cache.put(ipAddress, new CacheEntry(resolved, Instant.now()));
        return resolved;
    }

    @SuppressWarnings("unchecked")
    private Optional<GeoLocation> fetch(String ipAddress) {
        try {
            Map<String, Object> body = restClient.get()
                    .uri("/json/{ip}?fields=status,country,countryCode,regionName,city,lat,lon,timezone,isp", ipAddress)
                    .retrieve()
                    .body(Map.class);

            if (body == null || !"success".equals(body.get("status"))) {
                return Optional.empty();
            }
            return Optional.of(new GeoLocation(
                    (String) body.get("country"),
                    (String) body.get("countryCode"),
                    (String) body.get("regionName"),
                    (String) body.get("city"),
                    asDouble(body.get("lat")),
                    asDouble(body.get("lon")),
                    (String) body.get("timezone"),
                    (String) body.get("isp")
            ));
        } catch (Exception e) {
            log.debug("IP geolocation lookup failed (non-fatal): {}", e.getMessage());
            return Optional.empty();
        }
    }

    private Double asDouble(Object value) {
        return value instanceof Number number ? number.doubleValue() : null;
    }

    private boolean isPrivateOrLoopback(String ip) {
        if (ip == null) {
            return true;
        }
        if (ip.equals("127.0.0.1") || ip.equals("0:0:0:0:0:0:0:1") || ip.equals("::1")
                || ip.startsWith("10.") || ip.startsWith("192.168.")) {
            return true;
        }
        return isInPrivate172Range(ip);
    }

    /** 172.16.0.0/12 covers 172.16.x.x-172.31.x.x only - a bare {@code startsWith("172.")} wrongly also blocked public addresses like 172.217.x.x (Google). */
    private boolean isInPrivate172Range(String ip) {
        if (!ip.startsWith("172.")) {
            return false;
        }
        String[] parts = ip.split("\\.");
        if (parts.length < 2) {
            return false;
        }
        try {
            int secondOctet = Integer.parseInt(parts[1]);
            return secondOctet >= 16 && secondOctet <= 31;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private record CacheEntry(Optional<GeoLocation> value, Instant cachedAt) {
        boolean isFresh(int ttlMinutes) {
            return cachedAt.plus(Duration.ofMinutes(ttlMinutes)).isAfter(Instant.now());
        }
    }
}
