package com.pureeats.catalog.dto;

import java.math.BigDecimal;

public record CouponApplyResponse(Long couponId, String code, BigDecimal discountAmount, BigDecimal payableAmount, boolean waivesDelivery) {
}
