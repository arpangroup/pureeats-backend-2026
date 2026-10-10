package com.pureeats.order.service;

import com.pureeats.catalog.service.SettingSchemaService;
import com.pureeats.catalog.service.SettingValueService;
import com.pureeats.domain.entity.Order;
import com.pureeats.domain.entity.Restaurant;
import com.pureeats.geo.distance.DistanceCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Order timing (Settings -> General -> Delivery time estimates):
 * T1 preparation (the restaurant's own, else the default), T2 delivery partner to restaurant, T3 restaurant to
 * customer (travel time from the active {@link DistanceCalculator} - Google Distance Matrix with traffic when the
 * server's distance provider is google, otherwise estimated from the distance). Base ETA = T1 + T2 + T3, stored on
 * the order when it's placed. The customer app counts that down {@link #customerSlowdown()} times slower than real
 * time; the partner app shows real-time phases (T2 to the restaurant, remaining T1, then live travel time).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderTimingService {

    private final SettingValueService settingValueService;
    private final DistanceCalculator distanceCalculator;
    private final com.pureeats.order.repository.OrderStatusLogRepository orderStatusLogRepository;

    /**
     * When the food should be ready: the restaurant's acceptance (else when the order was placed) + T1. Drives the
     * kitchen's countdown and the partner's "food ready in" countdown; it goes negative (late) once passed.
     */
    public java.time.LocalDateTime prepDueAt(Order order) {
        java.time.LocalDateTime accepted = orderStatusLogRepository.findByOrderIdOrderByCreatedAtAsc(order.getId()).stream()
                .filter(e -> com.pureeats.domain.enums.OrderStatusCode.RESTAURANT_ACCEPTED.name().equals(e.getToStatus()))
                .map(com.pureeats.order.entity.OrderStatusLog::getCreatedAt)
                .findFirst()
                .orElse(order.getCreatedAt());
        int prep = order.getPrepareTime() != null && order.getPrepareTime() > 0 ? order.getPrepareTime() : 20;
        return accepted != null ? accepted.plusMinutes(prep) : null;
    }

    /** T1 for a restaurant. */
    public int prepMinutes(Restaurant restaurant) {
        if (restaurant != null && restaurant.getPreparationTime() != null && restaurant.getPreparationTime() > 0) {
            return restaurant.getPreparationTime();
        }
        return positive(settingValueService.getString(SettingSchemaService.DEFAULT_PREP_TIME_MINUTES, null), 20);
    }

    /** T2. */
    public int riderToRestaurantMinutes() {
        return positive(settingValueService.getString(SettingSchemaService.RIDER_TO_RESTAURANT_MINUTES, null), 10);
    }

    /** How many times slower than real time the customer's countdown runs (>= 1). */
    public double customerSlowdown() {
        String raw = settingValueService.getString(SettingSchemaService.CUSTOMER_ETA_SLOWDOWN, null);
        try {
            double v = raw != null ? Double.parseDouble(raw.trim()) : 1.5;
            return v >= 1 && v <= 5 ? v : 1.5;
        } catch (NumberFormatException e) {
            return 1.5;
        }
    }

    /** Travel minutes between two points (T3, or live partner -> customer). At least 1. */
    public int travelMinutes(String fromLat, String fromLng, String toLat, String toLng) {
        try {
            return Math.max(1, distanceCalculator.etaMinutes(fromLat, fromLng, toLat, toLng));
        } catch (RuntimeException e) {
            log.warn("Travel time lookup failed: {}", e.getMessage());
            return 1;
        }
    }

    public int travelMinutes(BigDecimal fromLat, BigDecimal fromLng, String toLat, String toLng) {
        return travelMinutes(fromLat != null ? fromLat.toPlainString() : null, fromLng != null ? fromLng.toPlainString() : null, toLat, toLng);
    }

    /** Sets T1, T2, T3 and the base ETA on a new order. Self-pickup: no T2/T3. */
    public void initialise(Order order, Restaurant restaurant, boolean selfPickup, String customerLat, String customerLng) {
        int t1 = prepMinutes(restaurant);
        int t2 = selfPickup ? 0 : riderToRestaurantMinutes();
        int t3 = selfPickup || restaurant == null ? 0 : travelMinutes(restaurant.getLatitude(), restaurant.getLongitude(), customerLat, customerLng);
        order.setPrepareTime(t1);
        order.setRiderToRestaurantMinutes(t2);
        order.setTravelMinutes(t3);
        order.setEtaMinutes(t1 + t2 + t3);
    }

    private static int positive(String raw, int fallback) {
        try {
            int v = raw != null ? Integer.parseInt(raw.trim()) : fallback;
            return v > 0 ? v : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
