package com.pureeats.order.dto;

import java.math.BigDecimal;

/**
 * How an order's charges were actually computed - captured once at placement time and persisted
 * (see {@code Order.pricingBreakdown}) so it reflects what really happened even if the
 * restaurant's rates change later.
 */
public record PricingBreakdown(
        BigDecimal itemTotal,
        BigDecimal discountAmount,
        BigDecimal amountAfterDiscount,
        BigDecimal taxAmount,
        BigDecimal taxPercentage,
        BigDecimal restaurantChargeAmount,
        BigDecimal restaurantChargePercentage,
        BigDecimal deliveryChargeAmount,
        /** "FIXED", "DYNAMIC", "SELF_PICKUP" or "FREE_DELIVERY_COUPON". */
        String deliveryChargeBasis,
        BigDecimal distanceKm,
        String restaurantLatitude,
        String restaurantLongitude,
        String customerLatitude,
        String customerLongitude,
        /** Delivery rates applied at order time; null for orders placed before this was recorded (and for self-pickup / free-delivery). */
        DeliveryChargeRates deliveryChargeRates
) {
    /** Pre-rates shape - existing callers (seeders) keep compiling; stored without rates. */
    public PricingBreakdown(BigDecimal itemTotal, BigDecimal discountAmount, BigDecimal amountAfterDiscount, BigDecimal taxAmount,
                            BigDecimal taxPercentage, BigDecimal restaurantChargeAmount, BigDecimal restaurantChargePercentage,
                            BigDecimal deliveryChargeAmount, String deliveryChargeBasis, BigDecimal distanceKm,
                            String restaurantLatitude, String restaurantLongitude, String customerLatitude, String customerLongitude) {
        this(itemTotal, discountAmount, amountAfterDiscount, taxAmount, taxPercentage, restaurantChargeAmount, restaurantChargePercentage,
                deliveryChargeAmount, deliveryChargeBasis, distanceKm, restaurantLatitude, restaurantLongitude, customerLatitude, customerLongitude, null);
    }
}
