package com.pureeats.order.service.cartvalidation;

import com.pureeats.domain.enums.DeliveryType;
import org.springframework.stereotype.Component;

import java.util.List;

/** Blocks a delivery order whose customer address is further than the restaurant's posted delivery radius. Distance already affected the dynamic delivery charge - this is what actually stops the order. */
@Component
public class DeliveryRadiusRule implements CartValidationRule {

    @Override
    public List<CartIssue> evaluate(CartValidationContext context) {
        if (context.deliveryType() != DeliveryType.DELIVERY || context.distanceKm() == null || context.restaurant().getDeliveryRadius() == null) {
            return List.of();
        }
        if (context.distanceKm().compareTo(context.restaurant().getDeliveryRadius()) > 0) {
            return List.of(CartIssue.restaurantLevel(
                    "Delivery address is " + context.distanceKm() + " km away - outside this restaurant's "
                            + context.restaurant().getDeliveryRadius() + " km delivery radius"));
        }
        return List.of();
    }
}
