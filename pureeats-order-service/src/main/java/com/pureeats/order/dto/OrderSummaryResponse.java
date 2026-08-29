package com.pureeats.order.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderSummaryResponse(
        Long id,
        String uniqueOrderId,
        String status,
        Long restaurantId,
        BigDecimal total,
        LocalDateTime createdAt
) {
}
