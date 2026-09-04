package com.pureeats.catalog.dto;

import java.math.BigDecimal;
import java.util.List;

/** All fields nullable - only non-null values are applied. Deliberately excludes restaurantId (no re-parenting an item via patch). */
public record ItemPatchRequest(
        Long itemCategoryId,
        String name,
        BigDecimal price,
        BigDecimal oldPrice,
        String image,
        String desc,
        Boolean isRecommended,
        Boolean isPopular,
        Boolean isVeg,
        Boolean isActive,
        /** Null = leave unchanged; an (possibly empty) list replaces the item's addon categories wholesale. */
        List<Long> addonCategoryIds
) {
}
