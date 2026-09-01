package com.pureeats.order.dto;

/** {@code reason} is null when {@code available} is true. */
public record CartItemValidationResponse(Long itemId, boolean available, String reason) {
}
