package com.pureeats.order.dto;

import java.math.BigDecimal;

/** couponId is null if the coupon has since been deleted - code/name/amount are still the order-time snapshot either way. */
public record OrderCouponSummary(Long couponId, String code, String name, String discountType, BigDecimal discountAmount) {
}
