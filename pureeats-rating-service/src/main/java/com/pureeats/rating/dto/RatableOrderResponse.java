package com.pureeats.rating.dto;

public record RatableOrderResponse(Long orderId, String uniqueOrderId, Long restaurantId) {
}
