package com.pureeats.order.service;

import com.pureeats.catalog.geo.DistanceCalculator;
import com.pureeats.domain.entity.Restaurant;
import com.pureeats.order.dto.DeliveryChargeResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Centralizes the tax/restaurant-charge/delivery-charge math that Laravel had copy-pasted per-controller. */
@Service
@Slf4j
@RequiredArgsConstructor
public class OrderPricingService {

    private final DistanceCalculator distanceCalculator;

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
        BigDecimal distanceKm = distanceKm(restaurant, customerLatitude, customerLongitude);

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
            log.debug("Computed dynamic delivery charge {} for restaurant {} at distance {}km", charge, restaurant.getId(), distanceKm);
            return new DeliveryChargeResult(charge, distanceKm, "DYNAMIC");
        }
        BigDecimal flat = restaurant.getDeliveryCharges() != null ? restaurant.getDeliveryCharges() : BigDecimal.ZERO;
        return new DeliveryChargeResult(flat, distanceKm, "FIXED");
    }

    /** Standalone distance lookup - lets a caller (e.g. cart-validation rules) know the distance before/independent of computing a delivery charge from it. Null customer coordinates (no address chosen yet) yield zero, same fallback {@link #computeDeliveryCharge} already had, since every {@link DistanceCalculator} implementation guarantees that on unparseable input. */
    public BigDecimal distanceKm(Restaurant restaurant, String customerLatitude, String customerLongitude) {
        return distanceCalculator.distanceKm(restaurant.getLatitude(), restaurant.getLongitude(), customerLatitude, customerLongitude);
    }

    private BigDecimal percentOf(BigDecimal amount, BigDecimal percentage) {
        if (percentage == null) {
            return BigDecimal.ZERO;
        }
        return amount.multiply(percentage).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
}
