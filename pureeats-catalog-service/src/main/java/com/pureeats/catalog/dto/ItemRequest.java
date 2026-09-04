package com.pureeats.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record ItemRequest(
        @NotNull Long itemCategoryId,
        @NotBlank String name,
        @NotNull BigDecimal price,
        BigDecimal oldPrice,
        String image,
        String desc,
        boolean isRecommended,
        boolean isPopular,
        boolean isVeg,
        /** Optional - omit to leave the item's addon categories unchanged. */
        List<Long> addonCategoryIds
) {
}
