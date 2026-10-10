package com.pureeats.catalog.service;

import com.pureeats.geo.distance.DistanceSettings;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Settings -> General -> Distance &amp; travel time, read by the app's single DistanceCalculator on every call. The
 * Google key is the one Google Maps key under Settings -> Google Map (App config) - not a second copy.
 * Unset values fall back to the {@code pureeats.distance.*} properties, so a server already configured with
 * {@code provider=google} keeps using Google until an admin chooses otherwise. Values are re-read at most every
 * {@link #REFRESH_MS} - a restaurant list asks once per restaurant, which shouldn't be one query each.
 */
@Component
@RequiredArgsConstructor
public class AdminDistanceSettings implements DistanceSettings {

    static final long REFRESH_MS = 15_000;

    private final SettingValueService settingValueService;
    private final AppConfigService appConfigService;

    @Value("${pureeats.distance.provider:haversine}")
    private String propertyProvider;
    @Value("${pureeats.distance.google.api-key:}")
    private String propertyApiKey;

    private volatile Snapshot snapshot;

    private record Snapshot(String method, String apiKey, long at) {
    }

    @Override
    public String method() {
        return current().method();
    }

    @Override
    public String googleApiKey() {
        return current().apiKey();
    }

    /** Drops the cached values - called after an admin saves settings so the change applies at once. */
    public void refresh() {
        snapshot = null;
    }

    private Snapshot current() {
        Snapshot s = snapshot;
        long now = System.currentTimeMillis();
        if (s != null && now - s.at() < REFRESH_MS) return s;
        String fallbackMethod = "google".equalsIgnoreCase(propertyProvider) ? GOOGLE : STRAIGHT_LINE;
        String method = settingValueService.getString(SettingSchemaService.DISTANCE_METHOD, fallbackMethod);
        String appKey = appConfigService.getGoogleMapsApiKey();
        String key = appKey != null && !appKey.isBlank() ? appKey.trim() : propertyApiKey;
        s = new Snapshot(GOOGLE.equalsIgnoreCase(method) ? GOOGLE : STRAIGHT_LINE, key, now);
        snapshot = s;
        return s;
    }
}
