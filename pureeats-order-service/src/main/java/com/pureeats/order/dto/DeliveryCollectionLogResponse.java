package com.pureeats.order.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DeliveryCollectionLogResponse(
        Long id,
        Long deliveryCollectionId,
        BigDecimal amount,
        /** "credit" | "debit". */
        String type,
        String message,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
