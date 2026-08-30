package com.pureeats.order.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AdminDeliveryCollectionResponse(
        Long id,
        Long userId,
        String riderName,
        BigDecimal amount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
