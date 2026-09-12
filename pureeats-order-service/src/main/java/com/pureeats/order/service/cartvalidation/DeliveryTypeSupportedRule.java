package com.pureeats.order.service.cartvalidation;

import com.pureeats.domain.entity.Restaurant;
import com.pureeats.domain.enums.DeliveryType;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Blocks selecting DELIVERY for a restaurant whose own {@code deliveryType} is self-pickup-only
 * (the legacy 0/1/2 column - 0 means self-pickup-only, see RestaurantService#DELIVERY_TYPE_TO_LABEL).
 * {@link DeliveryRadiusRule} only catches this by coincidence, when the computed distance to an
 * address the restaurant was never going to deliver to happens to exceed its (otherwise irrelevant,
 * since delivery isn't offered at all) deliveryRadius - a self-pickup-only restaurant with no radius
 * set, or a customer address that happens to fall within it, would otherwise slip through with a
 * flat delivery charge on a "delivery" that could never actually happen.
 */
@Component
public class DeliveryTypeSupportedRule implements CartValidationRule {

    private static final int SELF_PICKUP_ONLY = 0;

    @Override
    public List<CartIssue> evaluate(CartValidationContext context) {
        if (context.deliveryType() != DeliveryType.DELIVERY) {
            return List.of();
        }
        Restaurant restaurant = context.restaurant();
        if (restaurant.getDeliveryType() != null && restaurant.getDeliveryType() == SELF_PICKUP_ONLY) {
            return List.of(CartIssue.restaurantLevel("This restaurant only offers self-pickup, not delivery"));
        }
        return List.of();
    }
}
