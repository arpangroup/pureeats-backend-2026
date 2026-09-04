package com.pureeats.catalog.geo;

import java.math.BigDecimal;

/**
 * Straight-line distance between two lat/lng points, in kilometers. This is the single
 * abstraction {@code OrderPricingService} (delivery-charge tiers), {@code RestaurantService}
 * (delivery-area check + nearby-restaurant search) and the cart-validation pipeline's
 * {@code DeliveryRadiusRule} all consume - before this existed, order-service and catalog-service
 * each had their own copy-pasted haversine implementation.
 * <p>
 * Swapping the algorithm (or calling out to a paid API like Google's Distance Matrix, which
 * accounts for real road routing instead of a straight line) is purely a matter of which
 * implementation is {@code @ConditionalOnProperty}-selected in {@link DistanceCalculatorConfig} -
 * no caller of this interface ever changes.
 */
public interface DistanceCalculator {
    /** Null/unparseable coordinates on either end yield {@link BigDecimal#ZERO} - every implementation must honor this, never throw. */
    BigDecimal distanceKm(String lat1, String lng1, String lat2, String lng2);
}
