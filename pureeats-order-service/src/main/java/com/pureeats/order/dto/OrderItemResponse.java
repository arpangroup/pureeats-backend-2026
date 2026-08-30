package com.pureeats.order.dto;

import java.math.BigDecimal;
import java.util.List;

public record OrderItemResponse(
        Long id,
        Long itemId,
        String name,
        int quantity,
        BigDecimal price,
        List<OrderItemAddonResponse> addons
) {
}
