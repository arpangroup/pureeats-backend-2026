package com.pureeats.order.dto;

import com.pureeats.domain.enums.DeliveryType;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * The admin Cart Simulator's request shape - deliberately close to {@link CartValidationRequest}
 * but swaps a customer's saved {@code addressId} for a map-picked {@code customerLat}/
 * {@code customerLng}, since an admin testing "what would this cart cost from this location" isn't
 * necessarily any real customer. {@code paymentMode} is optional here (unlike the real order-placement
 * request) so the simulator can also test what happens with none chosen yet, same as the live Cart
 * preview.
 */
public record AdminCartSimulateRequest(
        @NotNull Long restaurantId,
        @NotEmpty List<PlaceOrderItemRequest> items,
        @NotNull DeliveryType deliveryType,
        String customerLat,
        String customerLng,
        String couponCode,
        String paymentMode
) {
}
