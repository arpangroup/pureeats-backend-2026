package com.pureeats.catalog.dto;

import java.math.BigDecimal;

/** Everything the caller (order-service) needs after successfully redeeming a coupon - avoids a second lookup for the display name. */
public record CouponRedemptionResult(
        Long couponId,
        String code,
        String name,
        BigDecimal discountAmount,
        boolean freeDelivery
) {
}
