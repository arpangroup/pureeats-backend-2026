package com.pureeats.order.service;

import com.pureeats.catalog.repository.RestaurantRepository;
import com.pureeats.domain.entity.*;
import com.pureeats.domain.enums.CommissionBasis;
import com.pureeats.order.dto.OrderEarningsSplitResponse;
import com.pureeats.order.dto.PricingBreakdown;
import com.pureeats.order.repository.AcceptDeliveryRepository;
import com.pureeats.order.repository.TripDetailRepository;
import com.pureeats.user.repository.DeliveryGuyDetailRepository;
import com.pureeats.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Builds the admin "who earns what" split for one order - see {@link OrderEarningsSplitResponse} for the formulas. */
@Service
@RequiredArgsConstructor
public class OrderEarningsService {

    private final OrderService orderService;
    private final OrderPricingService orderPricingService;
    private final RestaurantRepository restaurantRepository;
    private final AcceptDeliveryRepository acceptDeliveryRepository;
    private final TripDetailRepository tripDetailRepository;
    private final UserRepository userRepository;
    private final DeliveryGuyDetailRepository deliveryGuyDetailRepository;
    private final OrderStatusService orderStatusService;

    /** Same setting DeliveryOrderService credits riders with. */
    @Value("${pureeats.commission.basis:FULL_ORDER}")
    private CommissionBasis commissionBasis;

    @Transactional(readOnly = true)
    public OrderEarningsSplitResponse split(Long orderId) {
        Order order = orderService.findOrThrow(orderId);
        PricingBreakdown b = orderService.breakdownOf(order);
        Restaurant restaurant = restaurantRepository.findById(order.getRestaurantId().longValue()).orElse(null);

        BigDecimal itemTotal = nz(order.getTotal());
        BigDecimal discount = nz(order.getDiscountAmount());
        BigDecimal packaging = nz(order.getRestaurantCharge());
        BigDecimal deliveryCharge = nz(order.getDeliveryCharge());
        BigDecimal platformFee = nz(order.getPlatformFee());
        BigDecimal tip = nz(order.getDriverTipAmount());
        BigDecimal tax = nz(order.getTax());

        boolean snapshot = b != null && b.commissionPercentage() != null;
        BigDecimal commissionPct = snapshot ? b.commissionPercentage() : orderPricingService.commissionPercentage(restaurant);
        BigDecimal commission = snapshot && b.commissionAmount() != null ? b.commissionAmount() : orderPricingService.commission(itemTotal, commissionPct);
        BigDecimal restaurantAmount = orderService.restaurantPayoutFor(order);
        boolean storeOwnRate = restaurant != null && restaurant.getCommissionRate() != null && restaurant.getCommissionRate().signum() > 0;

        TripDetail trip = tripDetailRepository.findByOrderId(order.getId().intValue()).orElse(null);
        var restaurantShare = new OrderEarningsSplitResponse.RestaurantShare(order.getRestaurantId().longValue(),
                restaurant != null ? restaurant.getName() : "Restaurant", itemTotal, commissionPct, commission, storeOwnRate,
                packaging, restaurantAmount, isCompleted(order));

        OrderEarningsSplitResponse.RiderShare riderShare = riderShare(order, trip, tip, deliveryCharge);
        BigDecimal riderCommission = riderShare.commissionAmount();

        BigDecimal platformAmount = commission.add(platformFee).add(deliveryCharge).subtract(riderCommission).subtract(discount)
                .setScale(2, RoundingMode.HALF_UP);
        var platformShare = new OrderEarningsSplitResponse.PlatformShare(commission, platformFee, deliveryCharge, riderCommission, discount, platformAmount);

        return new OrderEarningsSplitResponse(nz(order.getPayable()), tax, restaurantShare, riderShare, platformShare, snapshot);
    }

    private OrderEarningsSplitResponse.RiderShare riderShare(Order order, TripDetail trip, BigDecimal tip, BigDecimal deliveryCharge) {
        AcceptDelivery assignment = acceptDeliveryRepository.findByOrderId(order.getId().intValue()).orElse(null);
        if (assignment == null) {
            return new OrderEarningsSplitResponse.RiderShare(false, null, null, null, commissionBasis.name(), null,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false);
        }
        Long riderUserId = assignment.getUserId().longValue();
        User user = userRepository.findById(riderUserId).orElse(null);
        DeliveryGuyDetail detail = user != null && user.getDeliveryGuyDetailId() != null
                ? deliveryGuyDetailRepository.findById(user.getDeliveryGuyDetailId().longValue()).orElse(null) : null;
        BigDecimal rate = detail != null && detail.getCommissionRate() != null ? detail.getCommissionRate() : BigDecimal.ZERO;
        BigDecimal base = commissionBasis == CommissionBasis.DELIVERY_CHARGE_ONLY ? deliveryCharge : nz(order.getTotal());
        BigDecimal commission = base.multiply(rate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal paidTip = tip;
        BigDecimal amount = commission.add(paidTip);
        if (trip != null && trip.getRiderEarning() != null) {
            // Delivered: the recorded earning (commission + tip at delivery time) is the source of truth.
            amount = trip.getRiderEarning();
            commission = amount.subtract(paidTip).max(BigDecimal.ZERO);
        }
        String name = detail != null && detail.getName() != null ? detail.getName() : user != null ? user.getName() : "Delivery partner";
        return new OrderEarningsSplitResponse.RiderShare(true, riderUserId, name, rate, commissionBasis.name(), base, commission, paidTip,
                amount.setScale(2, RoundingMode.HALF_UP), trip != null);
    }

    /** The restaurant's earning is recorded when the order completes - delivered by a rider or picked up by the customer. */
    private boolean isCompleted(Order order) {
        var status = orderStatusService.codeFor(order.getOrderstatusId());
        return status == com.pureeats.domain.enums.OrderStatusCode.DELIVERED || status == com.pureeats.domain.enums.OrderStatusCode.SELF_PICKUP_COMPLETED;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
