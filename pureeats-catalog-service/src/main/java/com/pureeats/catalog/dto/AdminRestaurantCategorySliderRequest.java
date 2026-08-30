package com.pureeats.catalog.dto;

import jakarta.validation.constraints.NotBlank;

public record AdminRestaurantCategorySliderRequest(
        @NotBlank String name,
        Boolean isActive
) {
}
