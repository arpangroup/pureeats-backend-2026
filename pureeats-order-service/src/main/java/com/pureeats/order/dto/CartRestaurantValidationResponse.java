package com.pureeats.order.dto;

/** {@code reason} is null when {@code available} is true. */
public record CartRestaurantValidationResponse(boolean available, String reason) {
}
