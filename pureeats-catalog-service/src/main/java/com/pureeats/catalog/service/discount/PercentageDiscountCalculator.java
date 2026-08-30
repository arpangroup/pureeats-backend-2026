package com.pureeats.catalog.service.discount;

import com.pureeats.domain.entity.Coupon;
import com.pureeats.domain.enums.DiscountType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** A percentage of the item total off, capped at the coupon's uptoAmount. */
@Component
public class PercentageDiscountCalculator implements DiscountCalculator {

    @Override
    public DiscountType type() {
        return DiscountType.PERCENTAGE;
    }

    @Override
    public BigDecimal calculateDiscount(Coupon coupon, BigDecimal orderAmount) {
        BigDecimal percentage = new BigDecimal(coupon.getDiscount());
        BigDecimal discount = orderAmount.multiply(percentage).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal cap = new BigDecimal(coupon.getUptoAmount());
        return discount.compareTo(cap) > 0 ? cap : discount;
    }
}
