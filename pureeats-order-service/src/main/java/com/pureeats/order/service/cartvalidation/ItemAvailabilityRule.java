package com.pureeats.order.service.cartvalidation;

import com.pureeats.domain.entity.Item;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Covers "item unavailable" for either of: deleted entirely, or deactivated/moved off this restaurant's menu. */
@Component
public class ItemAvailabilityRule implements CartValidationRule {

    @Override
    public List<CartIssue> evaluate(CartValidationContext context) {
        List<CartIssue> issues = new ArrayList<>();
        for (CartLine line : context.lines()) {
            Item item = context.itemFor(line);
            if (item == null) {
                issues.add(new CartIssue(line.itemId(), "This item is no longer available"));
            } else if (!item.getRestaurantId().equals(context.restaurant().getId().intValue()) || !Boolean.TRUE.equals(item.getIsActive())) {
                issues.add(new CartIssue(line.itemId(), "This item is no longer available at this restaurant"));
            }
        }
        return issues;
    }
}
