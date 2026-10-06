package com.pureeats.order.dto;

import java.math.BigDecimal;

/**
 * The rates a restaurant's orders are priced with right now - lets a guest cart (no account, so no
 * /cart/validate) estimate tax, packaging charge and platform fee with the same numbers the server
 * will charge. {@code platformFeeRate} is ₹ for FLAT, % for PERCENTAGE; {@code platformFeeCap} is the
 * PERCENTAGE cap in ₹ (null = none).
 */
public record PricingRatesResponse(
        BigDecimal taxPercentage,
        BigDecimal restaurantChargePercentage,
        String platformFeeType,
        BigDecimal platformFeeRate,
        BigDecimal platformFeeCap
) {
}
