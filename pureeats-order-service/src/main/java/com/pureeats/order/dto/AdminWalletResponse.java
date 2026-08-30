package com.pureeats.order.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AdminWalletResponse(
        Long id,
        String holderType,
        Long holderId,
        String name,
        String slug,
        String description,
        BigDecimal balance,
        Integer decimalPlaces,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
