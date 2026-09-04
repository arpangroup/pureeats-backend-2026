package com.pureeats.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

/** Admin/bulk item creation - unlike {@link ItemRequest}, carries its own restaurantId since it isn't scoped to one owner's path. */
public record AdminItemCreateRequest(
        @NotNull Long restaurantId,
        @NotNull Long itemCategoryId,
        @NotBlank String name,
        @NotNull BigDecimal price,
        BigDecimal oldPrice,
        String image,
        String desc,
        boolean isRecommended,
        boolean isPopular,
        boolean isVeg,
        /** Optional - omit to leave the item's addon categories unset (or unchanged, for bulk-upsert-style callers). */
        List<Long> addonCategoryIds
) {
}
