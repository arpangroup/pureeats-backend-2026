package com.pureeats.geo.distance;

import com.pureeats.geo.LatLng;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Real road distance and driving time (with live traffic) via Google's Distance Matrix API. Same
 * stub-until-configured shape as {@code FcmSender}: with no API key, or when Google fails, every call falls back
 * to {@link HaversineDistanceCalculator} rather than throwing, so selecting this is always safe.
 * <p>
 * One request returns both the distance and the duration, and results are cached for {@link #CACHE_TTL_MS}
 * (keyed by coordinates rounded to ~1 m), so a restaurant page, the cart and checkout for the same address don't
 * each pay for their own lookup. Several origins to one destination go out as one request of up to
 * {@link #MAX_ORIGINS_PER_REQUEST} origins.
 */
@Slf4j
public class GoogleDistanceMatrixCalculator implements DistanceCalculator {

    /** Distance Matrix allows 25 origins per request. */
    static final int MAX_ORIGINS_PER_REQUEST = 25;
    /** Driving times change with traffic - don't keep them long. */
    static final long CACHE_TTL_MS = 10 * 60 * 1000L;
    private static final int CACHE_MAX_ENTRIES = 5000;

    private final RestClient restClient;
    private final Supplier<String> apiKey;
    private final HaversineDistanceCalculator fallback = new HaversineDistanceCalculator();
    private final Map<String, CachedEstimate> cache = new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CachedEstimate> eldest) {
            return size() > CACHE_MAX_ENTRIES;
        }
    };

    private record CachedEstimate(TravelEstimate estimate, long at) {
    }

    public GoogleDistanceMatrixCalculator(String apiKey, int timeoutMs) {
        this(() -> apiKey, timeoutMs);
    }

    /** The key is read on every call, so changing it in admin Settings takes effect immediately. */
    public GoogleDistanceMatrixCalculator(Supplier<String> apiKey, int timeoutMs) {
        this.apiKey = apiKey;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);
        this.restClient = RestClient.builder()
                .baseUrl("https://maps.googleapis.com")
                .requestFactory(factory)
                .build();
    }

    @Override
    public BigDecimal distanceKm(String lat1, String lng1, String lat2, String lng2) {
        return estimate(lat1, lng1, lat2, lng2).distanceKm();
    }

    /** Real driving time (with live traffic when Google has it) - T3 and the partner's live drop-off ETA. */
    @Override
    public int etaMinutes(String lat1, String lng1, String lat2, String lng2) {
        return estimate(lat1, lng1, lat2, lng2).minutes();
    }

    @Override
    public TravelEstimate estimate(String lat1, String lng1, String lat2, String lng2) {
        return estimatesTo(List.of(new LatLng(lat1, lng1)), new LatLng(lat2, lng2)).get(0);
    }

    @Override
    public BigDecimal straightLineKm(String lat1, String lng1, String lat2, String lng2) {
        return fallback.distanceKm(lat1, lng1, lat2, lng2);
    }

    @Override
    public List<TravelEstimate> estimatesTo(List<LatLng> origins, LatLng destination) {
        TravelEstimate[] out = new TravelEstimate[origins.size()];
        String key = apiKey.get();
        boolean usable = key != null && !key.isBlank() && valid(destination);
        List<Integer> misses = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (int i = 0; i < origins.size(); i++) {
            LatLng origin = origins.get(i);
            if (!usable || !valid(origin)) {
                out[i] = straightLine(origin, destination);
                continue;
            }
            CachedEstimate hit = cached(cacheKey(origin, destination), now);
            if (hit != null) out[i] = hit.estimate();
            else misses.add(i);
        }
        for (int from = 0; from < misses.size(); from += MAX_ORIGINS_PER_REQUEST) {
            List<Integer> batch = misses.subList(from, Math.min(from + MAX_ORIGINS_PER_REQUEST, misses.size()));
            List<TravelEstimate> fetched = fetch(batch.stream().map(origins::get).toList(), destination, key);
            for (int b = 0; b < batch.size(); b++) {
                int i = batch.get(b);
                TravelEstimate e = fetched != null ? fetched.get(b) : null;
                if (e != null) {
                    put(cacheKey(origins.get(i), destination), e, now);
                    out[i] = e;
                } else {
                    out[i] = straightLine(origins.get(i), destination);
                }
            }
        }
        return List.of(out);
    }

    /** One element per origin, null where Google had no route; null overall when the request failed. */
    @SuppressWarnings("unchecked")
    private List<TravelEstimate> fetch(List<LatLng> origins, LatLng destination, String key) {
        String originParam = origins.stream().map(o -> o.lat().trim() + "," + o.lng().trim()).collect(Collectors.joining("|"));
        try {
            Map<String, Object> body = restClient.get()
                    .uri("/maps/api/distancematrix/json?origins={o}&destinations={d}&mode=driving&departure_time=now&key={key}",
                            originParam, destination.lat().trim() + "," + destination.lng().trim(), key)
                    .retrieve()
                    .body(Map.class);
            if (body == null || !"OK".equals(body.get("status"))) {
                log.warn("Google Distance Matrix returned {} ({}) - using straight-line distance",
                        body != null ? body.get("status") : "no body", body != null ? body.get("error_message") : "");
                return null;
            }
            List<Map<String, Object>> rows = (List<Map<String, Object>>) body.get("rows");
            List<TravelEstimate> out = new ArrayList<>(origins.size());
            for (int i = 0; i < origins.size(); i++) {
                Map<String, Object> element = (Map<String, Object>) ((List<Map<String, Object>>) rows.get(i).get("elements")).get(0);
                if (!"OK".equals(element.get("status"))) {
                    out.add(null);
                    continue;
                }
                double meters = ((Number) ((Map<String, Object>) element.get("distance")).get("value")).doubleValue();
                Map<String, Object> duration = (Map<String, Object>) (element.containsKey("duration_in_traffic")
                        ? element.get("duration_in_traffic") : element.get("duration"));
                double seconds = ((Number) duration.get("value")).doubleValue();
                out.add(new TravelEstimate(BigDecimal.valueOf(meters / 1000.0).setScale(2, RoundingMode.HALF_UP),
                        (int) Math.ceil(seconds / 60.0)));
            }
            return out;
        } catch (Exception e) {
            log.warn("Google Distance Matrix lookup failed, using straight-line distance: {}", e.getMessage());
            return null;
        }
    }

    private TravelEstimate straightLine(LatLng a, LatLng b) {
        return fallback.estimate(a.lat(), a.lng(), b.lat(), b.lng());
    }

    private synchronized CachedEstimate cached(String key, long now) {
        CachedEstimate c = cache.get(key);
        if (c == null || now - c.at() > CACHE_TTL_MS) return null;
        return c;
    }

    private synchronized void put(String key, TravelEstimate e, long now) {
        cache.put(key, new CachedEstimate(e, now));
    }

    private static boolean valid(LatLng p) {
        if (p == null || p.lat() == null || p.lng() == null) return false;
        try {
            Double.parseDouble(p.lat());
            Double.parseDouble(p.lng());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** ~1 m precision - the same customer address always hits the same entry. */
    private static String cacheKey(LatLng a, LatLng b) {
        return round(a.lat()) + "," + round(a.lng()) + ">" + round(b.lat()) + "," + round(b.lng());
    }

    private static String round(String v) {
        return BigDecimal.valueOf(Double.parseDouble(v)).setScale(5, RoundingMode.HALF_UP).toPlainString();
    }
}
