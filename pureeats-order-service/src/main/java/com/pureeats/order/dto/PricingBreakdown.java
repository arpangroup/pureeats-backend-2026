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
        String customerLongitude
) {
}
