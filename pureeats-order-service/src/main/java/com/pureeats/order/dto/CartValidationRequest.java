package com.pureeats.order.dto;

import com.pureeats.domain.enums.DeliveryType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** Same shape of "what's in the cart" as PlaceOrderRequest, minus the fields (address ownership already required, payment mode) that only matter once you actually commit to placing the order. */
public record CartValidationRequest(
        @NotNull Long restaurantId,
        @NotEmpty @Valid List<PlaceOrderItemRequest> items,
        Long addressId,
        String couponCode,
        DeliveryType deliveryType
) {
}
