package com.pureeats.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ItemRequest(
        @NotNull Long itemCategoryId,
        @NotBlank String name,
        @NotNull BigDecimal price,
        BigDecimal oldPrice,
        String image,
        String desc,
        boolean isRecommended,
        boolean isPopular,
        boolean isVeg
) {
}
