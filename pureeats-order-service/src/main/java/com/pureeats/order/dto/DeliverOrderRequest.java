package com.pureeats.order.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

/**
 * @param cashCollected cash-on-delivery orders only: what the partner actually received from the customer (at
 *                      least the amount due; anything over it is credited to the customer's wallet). Ignored for
 *                      prepaid orders and when the customer confirms delivery themself.
 */
public record DeliverOrderRequest(@NotBlank String deliveryPin, BigDecimal cashCollected) {
}
