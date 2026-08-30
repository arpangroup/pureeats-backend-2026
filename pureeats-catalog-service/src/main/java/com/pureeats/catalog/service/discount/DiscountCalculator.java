package com.pureeats.catalog.service.discount;

import com.pureeats.domain.entity.Coupon;
import com.pureeats.domain.enums.DiscountType;

import java.math.BigDecimal;

/**
 * One implementation per {@link DiscountType} - {@link com.pureeats.catalog.service.CouponService}
 * dispatches to whichever calculator matches a coupon's type instead of switching on the enum
 * inline, so a new discount type is a new bean here, not a change to CouponService's core logic.
 */
public interface DiscountCalculator {

    DiscountType type();

    /** The amount to subtract from the item total - zero for a calculator that discounts something else (e.g. delivery). */
    BigDecimal calculateDiscount(Coupon coupon, BigDecimal orderAmount);

    /** Whether this discount type waives the delivery charge entirely, instead of (or in addition to) discounting the item total. */
    default boolean waivesDeliveryCharge() {
        return false;
    }
}
