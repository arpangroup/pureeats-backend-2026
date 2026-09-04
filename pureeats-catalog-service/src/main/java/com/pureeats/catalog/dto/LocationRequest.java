package com.pureeats.catalog.dto;

import jakarta.validation.constraints.NotBlank;

public record LocationRequest(
        @NotBlank String name,
        String description,
        Boolean isPopular,
        Boolean isActive
) {
}
