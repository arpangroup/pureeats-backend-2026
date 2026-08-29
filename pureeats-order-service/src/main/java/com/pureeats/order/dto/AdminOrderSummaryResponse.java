package com.pureeats.order.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Admin-panel order row - unlike {@link OrderSummaryResponse} (self-scoped), this includes the customer/restaurant names. */
public record AdminOrderSummaryResponse(
        Long id,
        String uniqueOrderId,
        String status,
        Long userId,
        Long restaurantId,
        String customerName,
        String restaurantName,
        Integer itemCount,
        BigDecimal total,
        BigDecimal payable,
        String paymentMode,
        String couponName,
        LocalDateTime createdAt
) {
}
