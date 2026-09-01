package com.pureeats.order.service.cartvalidation;

import com.pureeats.domain.entity.Restaurant;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.util.List;

/** Covers "store unavailable" for any of: deactivated, paused by the owner, or outside its posted opening hours. */
@Component
public class RestaurantAvailabilityRule implements CartValidationRule {

    @Override
    public List<CartIssue> evaluate(CartValidationContext context) {
        Restaurant restaurant = context.restaurant();
        if (!Boolean.TRUE.equals(restaurant.getIsActive())) {
            return List.of(CartIssue.restaurantLevel("This restaurant is currently unavailable"));
        }
        if (!Boolean.TRUE.equals(restaurant.getIsAccepted())) {
            return List.of(CartIssue.restaurantLevel("This restaurant is not accepting orders right now"));
        }
        if (restaurant.getOpeningTime() != null && restaurant.getClosingTime() != null && !isWithinOpeningHours(restaurant)) {
            return List.of(CartIssue.restaurantLevel(
                    "This restaurant is closed right now (opens " + restaurant.getOpeningTime() + ")"));
        }
        return List.of();
    }

    private boolean isWithinOpeningHours(Restaurant restaurant) {
        LocalTime now = LocalTime.now();
        LocalTime opens = restaurant.getOpeningTime();
        LocalTime closes = restaurant.getClosingTime();
        if (opens.isBefore(closes)) {
            return !now.isBefore(opens) && !now.isAfter(closes);
        }
        // Overnight window (e.g. 18:00-02:00): "within hours" means at/after opening OR at/before closing.
        return !now.isBefore(opens) || !now.isAfter(closes);
    }
}
