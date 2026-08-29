package com.pureeats.order.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record PlaceOrderItemRequest(
        @NotNull Long itemId,
        @Positive int quantity,
        List<Long> selectedAddonIds
) {
}
