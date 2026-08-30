package com.pureeats.catalog.service.discount;

import com.pureeats.domain.entity.Coupon;
import com.pureeats.domain.enums.DiscountType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** A fixed rupee amount off the item total, capped at the coupon's uptoAmount. */
@Component
public class FlatDiscountCalculator implements DiscountCalculator {

    @Override
    public DiscountType type() {
        return DiscountType.AMOUNT;
    }

    @Override
    public BigDecimal calculateDiscount(Coupon coupon, BigDecimal orderAmount) {
        BigDecimal flatAmount = new BigDecimal(coupon.getDiscount());
        BigDecimal cap = new BigDecimal(coupon.getUptoAmount());
        return flatAmount.compareTo(cap) > 0 ? cap : flatAmount;
    }
}
