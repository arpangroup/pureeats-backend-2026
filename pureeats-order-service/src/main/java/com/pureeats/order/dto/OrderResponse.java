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
        /** Flat, admin-configurable fee snapshotted at order-placement time — 0 for orders placed before this existed. */
        BigDecimal platformFee,
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
        /** Last status-transition time - what the admin panel's order-journey diagram uses as the "when did this terminal state happen" timestamp. */
        LocalDateTime updatedAt,
        List<String> legalNextStatuses,
        /** Null for orders placed before this was tracked. */
        PricingBreakdown pricingBreakdown,
        /** Null until a rider has been assigned (via the delivery flow or admin override). */
        Long deliveryGuyId,
        String deliveryGuyName,
        /** Richer version of deliveryGuyId/deliveryGuyName above (kept for backward compat) — null until assigned. */
        OrderDeliveryPartnerSummary deliveryPartner,
        /** T1 - preparation minutes. */
        Integer prepTimeMinutes,
        /** T2 - delivery partner to restaurant minutes. */
        Integer riderToRestaurantMinutes,
        /** T3 - restaurant to customer travel minutes. */
        Integer travelMinutes,
        /** Base ETA = T1 + T2 + T3 from createdAt (null for orders placed before timing existed). */
        Integer etaMinutes,
        /** The customer app's countdown runs this many times slower than real time (Settings -> Delivery time estimates). */
        Double etaSlowdownFactor
) {
}
