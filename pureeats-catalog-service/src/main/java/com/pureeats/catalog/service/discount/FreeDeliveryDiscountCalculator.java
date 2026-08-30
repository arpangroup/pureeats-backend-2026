package com.pureeats.catalog.service.discount;

import com.pureeats.domain.entity.Coupon;
import com.pureeats.domain.enums.DiscountType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** Waives the delivery charge - doesn't touch the item total at all, so the "discount" here is always zero. */
@Component
public class FreeDeliveryDiscountCalculator implements DiscountCalculator {

    @Override
    public DiscountType type() {
        return DiscountType.FREE_DELIVERY;
    }

    @Override
    public BigDecimal calculateDiscount(Coupon coupon, BigDecimal orderAmount) {
        return BigDecimal.ZERO;
    }

    @Override
    public boolean waivesDeliveryCharge() {
        return true;
    }
}
