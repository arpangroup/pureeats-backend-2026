package com.pureeats.order.service.cartvalidation;

import com.pureeats.catalog.dto.RestaurantOpenStatus;
import com.pureeats.catalog.service.RestaurantOpenStatusService;
import com.pureeats.catalog.service.RestaurantScheduleCodec;
import com.pureeats.domain.entity.Restaurant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Covers "store unavailable" for any of: deactivated, paused by the owner, or outside its posted
 * hours. The hours check defers entirely to {@link RestaurantOpenStatusService} - the same
 * day-aware, multi-slot computation the customer-facing API responses use - so the checkout gate
 * can never disagree with what the customer was shown on the restaurant page.
 */
@Component
@RequiredArgsConstructor
public class RestaurantAvailabilityRule implements CartValidationRule {

    private final RestaurantScheduleCodec scheduleCodec;
    private final RestaurantOpenStatusService openStatusService;

    @Override
    public List<CartIssue> evaluate(CartValidationContext context) {
        Restaurant restaurant = context.restaurant();
        if (!Boolean.TRUE.equals(restaurant.getIsActive())) {
            return List.of(CartIssue.restaurantLevel("This restaurant is currently unavailable"));
        }
        if (!Boolean.TRUE.equals(restaurant.getIsAccepted())) {
            return List.of(CartIssue.restaurantLevel("This restaurant is not accepting orders right now"));
        }
        RestaurantOpenStatus status = openStatusService.compute(
                restaurant, scheduleCodec.deserialize(restaurant.getScheduleData()), LocalDateTime.now());
        if (!status.isOpenNow()) {
            String reopens = status.nextOpensAt() == null
                    ? ""
                    : " (opens " + (status.nextOpensLabel() == null ? "" : status.nextOpensLabel() + " ") + "at " + status.nextOpensAt() + ")";
            return List.of(CartIssue.restaurantLevel("This restaurant is closed right now" + reopens));
        }
        return List.of();
    }
}
