package com.pureeats.order.dto;

import java.math.BigDecimal;

/** {@code reason} is null when {@code valid} is true. */
public record CartCouponValidationResponse(boolean valid, String reason, BigDecimal discountAmount, boolean waivesDelivery) {
}
