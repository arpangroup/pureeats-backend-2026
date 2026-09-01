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
        BigDecimal payable
) {
}
