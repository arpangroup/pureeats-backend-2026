package com.pureeats.catalog.dto;

import java.math.BigDecimal;

/** Cross-restaurant item, for the Home page's "Recommended" section - ItemResponse plus the restaurant context a bare item card needs to render. */
public record RecommendedItemResponse(
        Long id,
        Long restaurantId,
        String restaurantName,
        String restaurantImage,
        Long itemCategoryId,
        String name,
        BigDecimal price,
        BigDecimal oldPrice,
        String image,
        String desc,
        boolean isVeg
) {
}
