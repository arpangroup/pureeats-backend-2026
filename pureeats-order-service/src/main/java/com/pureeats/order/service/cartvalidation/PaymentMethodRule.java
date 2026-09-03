package com.pureeats.order.service.cartvalidation;

import org.springframework.stereotype.Component;

import java.util.List;

/** Blocks Cash on Delivery when a restaurant has isAcceptCod=false - the flag existed but placeOrder never read it. Only ever fires at order-placement time (validate()'s live cart preview doesn't know the payment mode yet - it's chosen on Checkout, after Cart). */
@Component
public class PaymentMethodRule implements CartValidationRule {

    @Override
    public List<CartIssue> evaluate(CartValidationContext context) {
        if (context.paymentMode() == null) {
            return List.of();
        }
        if ("COD".equalsIgnoreCase(context.paymentMode()) && !Boolean.TRUE.equals(context.restaurant().getIsAcceptCod())) {
            return List.of(CartIssue.restaurantLevel("This restaurant does not accept Cash on Delivery"));
        }
        return List.of();
    }
}
