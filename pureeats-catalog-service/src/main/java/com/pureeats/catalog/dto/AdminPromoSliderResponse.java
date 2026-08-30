package com.pureeats.catalog.dto;

import java.time.LocalDateTime;

public record AdminPromoSliderResponse(
        Long id,
        String name,
        Boolean isActive,
        Integer locationId,
        Integer positionId,
        String size,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
