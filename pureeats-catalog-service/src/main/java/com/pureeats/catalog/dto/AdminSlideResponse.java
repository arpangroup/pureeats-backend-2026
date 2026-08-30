package com.pureeats.catalog.dto;

import java.time.LocalDateTime;

public record AdminSlideResponse(
        Long id,
        String sliderType,
        Long sliderId,
        String uniqueId,
        String name,
        String description,
        String image,
        String imagePlaceholder,
        String linkType,
        Long categoryId,
        Long restaurantId,
        String url,
        Integer positionId,
        Boolean isActive,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
