package com.pureeats.order.service.cartvalidation;

import org.springframework.stereotype.Component;

import java.util.List;

/** Blocks a cart whose item total doesn't meet the restaurant's minOrderPrice - the field existed but nothing checked it. */
@Component
public class MinimumOrderAmountRule implements CartValidationRule {

    @Override
    public List<CartIssue> evaluate(CartValidationContext context) {
        var minOrderPrice = context.restaurant().getMinOrderPrice();
        if (minOrderPrice == null || minOrderPrice.signum() <= 0) {
            return List.of();
        }
        if (context.rawItemTotal().compareTo(minOrderPrice) < 0) {
            return List.of(CartIssue.restaurantLevel(
                    "Item total ₹" + context.rawItemTotal() + " is below the ₹" + minOrderPrice + " minimum order amount for this restaurant"));
        }
        return List.of();
    }
}
