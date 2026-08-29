package com.pureeats.order.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(
        Long id,
        String uniqueOrderId,
        String status,
        Long restaurantId,
        String address,
        BigDecimal tax,
        BigDecimal restaurantCharge,
        BigDecimal deliveryCharge,
        BigDecimal driverTipAmount,
        BigDecimal total,
        BigDecimal payable,
        String paymentMode,
        String deliveryPin,
        String orderComment,
        LocalDateTime createdAt,
        List<OrderItemResponse> items
) {
}
