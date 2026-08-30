package com.pureeats.order.dto;

import com.pureeats.domain.enums.DeliveryType;
import com.pureeats.domain.enums.PaymentMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record PlaceOrderRequest(
        @NotNull Long restaurantId,
        @NotNull Long addressId,
        @NotEmpty @Valid List<PlaceOrderItemRequest> items,
        @NotNull PaymentMode paymentMode,
        @NotNull DeliveryType deliveryType,
        String couponCode,
        String orderComment,
        BigDecimal driverTipAmount
) {
}
