package com.pureeats.order.dto;

import java.math.BigDecimal;

/** {@code rates}: the restaurant rates applied (null for SELF_PICKUP / FREE_DELIVERY_COUPON). */
public record DeliveryChargeResult(BigDecimal amount, BigDecimal distanceKm, String basis, DeliveryChargeRates rates) {
    public DeliveryChargeResult(BigDecimal amount, BigDecimal distanceKm, String basis) {
        this(amount, distanceKm, basis, null);
    }
}
