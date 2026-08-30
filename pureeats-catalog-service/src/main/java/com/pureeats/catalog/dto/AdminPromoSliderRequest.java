package com.pureeats.catalog.dto;

import jakarta.validation.constraints.NotBlank;

public record AdminPromoSliderRequest(
        @NotBlank String name,
        Boolean isActive,
        Integer locationId,
        Integer positionId,
        @NotBlank String size
) {
}
