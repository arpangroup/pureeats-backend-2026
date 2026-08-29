package com.pureeats.order.service;

import com.pureeats.domain.entity.Restaurant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Centralizes the tax/restaurant-charge/delivery-charge math that Laravel had copy-pasted per-controller. */
@Service
public class OrderPricingService {

    @Value("${pureeats.tax.percentage:5}")
    private BigDecimal taxPercentage;

    public BigDecimal tax(BigDecimal amount) {
        return percentOf(amount, taxPercentage);
    }

    public BigDecimal restaurantCharge(Restaurant restaurant, BigDecimal amount) {
        return percentOf(amount, restaurant.getRestaurantCharges());
    }

    public BigDecimal deliveryCharge(Restaurant restaurant, boolean isSelfPickup) {
        return isSelfPickup ? BigDecimal.ZERO : restaurant.getDeliveryCharges();
    }

    private BigDecimal percentOf(BigDecimal amount, BigDecimal percentage) {
        if (percentage == null) {
            return BigDecimal.ZERO;
        }
        return amount.multiply(percentage).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
}
