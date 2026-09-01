package com.pureeats.order.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderSummaryResponse(
        Long id,
        String uniqueOrderId,
        String status,
        Long restaurantId,
        String restaurantName,
        String restaurantImage,
        BigDecimal total,
        LocalDateTime createdAt,
        /** Null until a rider has been assigned. */
        String deliveryGuyName
) {
}
