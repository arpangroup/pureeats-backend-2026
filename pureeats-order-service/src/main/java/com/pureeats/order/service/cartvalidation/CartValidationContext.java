package com.pureeats.order.service.cartvalidation;

import com.pureeats.domain.entity.Item;
import com.pureeats.domain.entity.Restaurant;

import java.util.List;
import java.util.Map;

/** {@code resolvedItems} only contains entries for item IDs that actually exist - a missing entry means the item was deleted/not found. */
public record CartValidationContext(Restaurant restaurant, List<CartLine> lines, Map<Long, Item> resolvedItems) {

    public Item itemFor(CartLine line) {
        return resolvedItems.get(line.itemId());
    }
}
