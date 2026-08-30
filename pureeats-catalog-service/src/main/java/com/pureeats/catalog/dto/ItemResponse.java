package com.pureeats.catalog.dto;

import java.math.BigDecimal;

public record ItemResponse(
        Long id,
        Long restaurantId,
        Long itemCategoryId,
        String name,
        BigDecimal price,
        BigDecimal oldPrice,
        String image,
        String desc,
        boolean isRecommended,
        boolean isPopular,
        boolean isNew,
        boolean isVeg,
        boolean isActive
) {
}
