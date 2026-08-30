package com.pureeats.order.service;

import com.pureeats.domain.entity.Restaurant;
import com.pureeats.order.dto.DeliveryChargeResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Centralizes the tax/restaurant-charge/delivery-charge math that Laravel had copy-pasted per-controller. */
@Service
public class OrderPricingService {

    private static final double EARTH_RADIUS_KM = 6371.0;

    @Value("${pureeats.tax.percentage:5}")
    private BigDecimal taxPercentage;

    public BigDecimal tax(BigDecimal amount) {
        return percentOf(amount, taxPercentage);
    }

    public BigDecimal taxPercentage() {
        return taxPercentage;
    }

    public BigDecimal restaurantCharge(Restaurant restaurant, BigDecimal amount) {
        return percentOf(amount, restaurant.getRestaurantCharges());
    }

    /**
     * Distance-aware delivery charge: "dynamic" restaurants charge a base amount up to a base
     * distance, then an extra increment per extra-distance step beyond it; "fixed" restaurants
     * (the default) just charge their flat {@code deliveryCharges} regardless of distance. Distance
     * is still computed and returned either way, for the pricing-breakdown snapshot.
     */
    public DeliveryChargeResult computeDeliveryCharge(Restaurant restaurant, boolean isSelfPickup, boolean freeDelivery,
                                                        String customerLatitude, String customerLongitude) {
        BigDecimal distanceKm = distanceBetween(restaurant.getLatitude(), restaurant.getLongitude(), customerLatitude, customerLongitude);

        if (isSelfPickup) {
            return new DeliveryChargeResult(BigDecimal.ZERO, distanceKm, "SELF_PICKUP");
        }
        if (freeDelivery) {
            return new DeliveryChargeResult(BigDecimal.ZERO, distanceKm, "FREE_DELIVERY_COUPON");
        }
        if ("dynamic".equalsIgnoreCase(restaurant.getDeliveryChargeType()) && restaurant.getBaseDeliveryCharge() != null) {
            BigDecimal charge = restaurant.getBaseDeliveryCharge();
            int baseDistance = restaurant.getBaseDeliveryDistance() != null ? restaurant.getBaseDeliveryDistance() : 0;
            Integer extraDistanceStep = restaurant.getExtraDeliveryDistance();
            if (distanceKm.doubleValue() > baseDistance && extraDistanceStep != null && extraDistanceStep > 0
                    && restaurant.getExtraDeliveryCharge() != null) {
                double extraKm = distanceKm.doubleValue() - baseDistance;
                int extraUnits = (int) Math.ceil(extraKm / extraDistanceStep);
                charge = charge.add(restaurant.getExtraDeliveryCharge().multiply(BigDecimal.valueOf(extraUnits)));
            }
            return new DeliveryChargeResult(charge, distanceKm, "DYNAMIC");
        }
        BigDecimal flat = restaurant.getDeliveryCharges() != null ? restaurant.getDeliveryCharges() : BigDecimal.ZERO;
        return new DeliveryChargeResult(flat, distanceKm, "FIXED");
    }

    private BigDecimal distanceBetween(String lat1, String lng1, String lat2, String lng2) {
        try {
            double distance = haversineKm(Double.parseDouble(lat1), Double.parseDouble(lng1),
                    Double.parseDouble(lat2), Double.parseDouble(lng2));
            return BigDecimal.valueOf(distance).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException | NullPointerException e) {
            return BigDecimal.ZERO;
        }
    }

    private static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }

    private BigDecimal percentOf(BigDecimal amount, BigDecimal percentage) {
        if (percentage == null) {
            return BigDecimal.ZERO;
        }
        return amount.multiply(percentage).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
}
