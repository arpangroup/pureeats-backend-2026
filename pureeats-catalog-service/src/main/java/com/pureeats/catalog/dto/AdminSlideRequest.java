package com.pureeats.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** sliderType is "promo" or "category" - selects which container sliderId refers to. */
public record AdminSlideRequest(
        @NotBlank String sliderType,
        @NotNull Long sliderId,
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
        Boolean isActive
) {
}
