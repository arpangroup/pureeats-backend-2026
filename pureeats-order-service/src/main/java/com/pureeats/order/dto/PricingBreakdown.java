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
        DeliveryChargeRates deliveryChargeRates,
        /** FLAT or PERCENTAGE; null on orders placed before platform fee modes existed. */
        String platformFeeType,
        /** ₹ amount (FLAT) or % (PERCENTAGE). */
        BigDecimal platformFeeRate,
        /** PERCENTAGE cap in ₹, null = none. */
        BigDecimal platformFeeCap,
        /** Platform's commission on the item total (store rate, else the default) - deducted from the restaurant payout. */
        BigDecimal commissionPercentage,
        BigDecimal commissionAmount,
        /** itemTotal − commission + restaurant (packaging) charge: what the restaurant is paid for this order. */
        BigDecimal restaurantPayout
) {
    /** Pre-fee-modes shape (with delivery rates) - stored without platform fee mode / commission / payout. */
    public PricingBreakdown(BigDecimal itemTotal, BigDecimal discountAmount, BigDecimal amountAfterDiscount, BigDecimal taxAmount,
                            BigDecimal taxPercentage, BigDecimal restaurantChargeAmount, BigDecimal restaurantChargePercentage,
                            BigDecimal deliveryChargeAmount, String deliveryChargeBasis, BigDecimal distanceKm,
                            String restaurantLatitude, String restaurantLongitude, String customerLatitude, String customerLongitude,
                            DeliveryChargeRates deliveryChargeRates) {
        this(itemTotal, discountAmount, amountAfterDiscount, taxAmount, taxPercentage, restaurantChargeAmount, restaurantChargePercentage,
                deliveryChargeAmount, deliveryChargeBasis, distanceKm, restaurantLatitude, restaurantLongitude, customerLatitude, customerLongitude,
                deliveryChargeRates, null, null, null, null, null, null);
    }

    /** Pre-rates shape - existing callers (seeders) keep compiling; stored without rates. */
    public PricingBreakdown(BigDecimal itemTotal, BigDecimal discountAmount, BigDecimal amountAfterDiscount, BigDecimal taxAmount,
                            BigDecimal taxPercentage, BigDecimal restaurantChargeAmount, BigDecimal restaurantChargePercentage,
                            BigDecimal deliveryChargeAmount, String deliveryChargeBasis, BigDecimal distanceKm,
                            String restaurantLatitude, String restaurantLongitude, String customerLatitude, String customerLongitude) {
        this(itemTotal, discountAmount, amountAfterDiscount, taxAmount, taxPercentage, restaurantChargeAmount, restaurantChargePercentage,
                deliveryChargeAmount, deliveryChargeBasis, distanceKm, restaurantLatitude, restaurantLongitude, customerLatitude, customerLongitude,
                (DeliveryChargeRates) null);
    }
}
