package com.pureeats.order.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(
        Long id,
        String uniqueOrderId,
        String status,
        Integer orderstatusId,
        OrderCustomerSummary customer,
        OrderRestaurantSummary restaurant,
        /** Null if no coupon was applied to this order. */
        OrderCouponSummary coupon,
        List<OrderItemResponse> items,
        String address,
        BigDecimal tax,
        BigDecimal restaurantCharge,
        BigDecimal deliveryCharge,
        BigDecimal driverTipAmount,
        BigDecimal discountAmount,
        BigDecimal total,
        BigDecimal payable,
        String paymentMode,
        String deliveryPin,
        String orderComment,
        String transactionId,
        Integer deliveryType,
        String orderFrom,
        LocalDateTime createdAt,
        List<String> legalNextStatuses,
        /** Null for orders placed before this was tracked. */
        PricingBreakdown pricingBreakdown,
        /** Null until a rider has been assigned (via the delivery flow or admin override). */
        Long deliveryGuyId,
        String deliveryGuyName
) {
}
