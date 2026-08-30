package com.pureeats.order.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AdminRestaurantPayoutResponse(
        Long id,
        Long restaurantId,
        String restaurantName,
        Long restaurantEarningId,
        BigDecimal amount,
        /** "pending" | "processing" | "paid" | "rejected". */
        String status,
        String transactionMode,
        String transactionId,
        String message,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
