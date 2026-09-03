package com.pureeats.order.dto;

import jakarta.validation.constraints.NotBlank;

/** {@code toStatus} accepts either the raw {@code OrderStatusCode} name or its display label - see {@link com.pureeats.domain.enums.OrderStatusCode#fromValue}. */
public record UpdateOrderStatusRequest(@NotBlank String toStatus) {
}
