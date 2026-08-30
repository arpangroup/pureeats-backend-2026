package com.pureeats.catalog.dto;

import java.time.LocalDateTime;

public record AdminRestaurantCategorySliderResponse(
        Long id,
        String name,
        Boolean isActive,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
