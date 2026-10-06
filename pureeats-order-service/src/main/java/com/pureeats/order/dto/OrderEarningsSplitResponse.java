package com.pureeats.order.dto;

import java.math.BigDecimal;

/**
 * Admin-only: who earns what from one order. The shares always reconcile:
 * {@code customerPaid = restaurant.amount + rider.amount + platform.amount + taxCollected}.
 * <ul>
 *   <li>Restaurant = item total − commission + packaging (restaurant) charge</li>
 *   <li>Delivery partner = commission on the commission base (order total or delivery charge, per the
 *       server's COMMISSION_BASIS) + the customer's tip</li>
 *   <li>Platform (PureEats) = commission + platform fee + delivery charge − rider commission − discount
 *       (coupons are platform-funded)</li>
 *   <li>Tax is collected for the government - nobody's earning</li>
 * </ul>
 * {@code finalized} = the share has actually been recorded (order delivered); otherwise it's what the
 * share will be at delivery, from the rates snapshotted on the order (or current rates for older ones).
 */
public record OrderEarningsSplitResponse(
        BigDecimal customerPaid,
        BigDecimal taxCollected,
        RestaurantShare restaurant,
        RiderShare rider,
        PlatformShare platform,
        /** True when the rates came from the order's own snapshot; false = current store/rider rates (older order). */
        boolean ratesFromSnapshot
) {
    public record RestaurantShare(Long restaurantId, String restaurantName, BigDecimal itemTotal,
                                  BigDecimal commissionPercentage, BigDecimal commissionAmount,
                                  /** True when the store has its own commission rate; false = the platform default was used. */
                                  boolean storeOwnRate,
                                  BigDecimal packagingCharge, BigDecimal amount, boolean finalized) {
    }

    public record RiderShare(boolean assigned, Long riderUserId, String riderName,
                             BigDecimal commissionRate,
                             /** FULL_ORDER or DELIVERY_CHARGE_ONLY. */
                             String commissionBasis,
                             BigDecimal commissionBase, BigDecimal commissionAmount, BigDecimal tip,
                             BigDecimal amount, boolean finalized) {
    }

    public record PlatformShare(BigDecimal commission, BigDecimal platformFee, BigDecimal deliveryCharge,
                                BigDecimal riderCommission, BigDecimal discount, BigDecimal amount) {
    }
}
