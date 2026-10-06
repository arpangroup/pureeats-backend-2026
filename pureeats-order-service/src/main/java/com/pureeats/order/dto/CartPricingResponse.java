package com.pureeats.order.dto;

import java.math.BigDecimal;

/** Computed from AVAILABLE items only (see CartValidationResponse.items) - an unavailable item never contributes to itemTotal/payable. */
public record CartPricingResponse(
        BigDecimal itemTotal,
        BigDecimal discountAmount,
        BigDecimal tax,
        BigDecimal restaurantCharge,
        BigDecimal deliveryCharge,
        String deliveryChargeBasis,
        BigDecimal distanceKm,
        BigDecimal platformFee,
        BigDecimal payable,
        /** Rates behind the amounts above, so the cart can label each line ("Tax (5%)", "Platform fee (2%)"). */
        BigDecimal taxPercentage,
        BigDecimal restaurantChargePercentage,
        /** FLAT or PERCENTAGE. */
        String platformFeeType,
        /** ₹ (FLAT) or % (PERCENTAGE). */
        BigDecimal platformFeeRate
) {
}
