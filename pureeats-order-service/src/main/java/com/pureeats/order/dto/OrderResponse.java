package com.pureeats.order.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(
        Long id,
        String uniqueOrderId,
        String status,
        Integer orderstatusId,
        Long userId,
        Long restaurantId,
        String customerName,
        String restaurantName,
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
        String couponName,
        String transactionId,
        Integer deliveryType,
        String orderFrom,
        LocalDateTime restaurantAcceptAt,
        LocalDateTime restaurantReadyAt,
        LocalDateTime riderAcceptAt,
        LocalDateTime riderPickedAt,
        LocalDateTime riderDeliverAt,
        LocalDateTime createdAt,
        List<OrderItemResponse> items,
        List<String> legalNextStatuses
) {
}
