package com.pureeats.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record AddonRequest(
        @NotNull Long addonCategoryId,
        @NotBlank String name,
        @NotNull BigDecimal price
) {
}
