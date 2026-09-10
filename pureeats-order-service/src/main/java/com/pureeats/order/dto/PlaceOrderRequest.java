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
        BigDecimal driverTipAmount,
        /** Only present (and only checked) when paymentMode is RAZORPAY — the three values Razorpay Checkout's success callback hands back. All three must verify (see OrderService#placeOrder) before a RAZORPAY order is ever persisted. */
        String razorpayOrderId,
        String razorpayPaymentId,
        String razorpaySignature
) {
}
