package com.pureeats.order.service.cartvalidation;

import com.pureeats.domain.entity.Item;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Covers "stock finished". Item.stockQuantity is null for the vast majority of items today (untracked/unlimited) - only items a store owner has explicitly set a count on are checked. */
@Component
public class ItemStockRule implements CartValidationRule {

    @Override
    public List<CartIssue> evaluate(CartValidationContext context) {
        List<CartIssue> issues = new ArrayList<>();
        for (CartLine line : context.lines()) {
            Item item = context.itemFor(line);
            if (item == null || item.getStockQuantity() == null) {
                continue;
            }
            if (item.getStockQuantity() <= 0) {
                issues.add(new CartIssue(line.itemId(), "Out of stock"));
            } else if (item.getStockQuantity() < line.quantity()) {
                issues.add(new CartIssue(line.itemId(), "Only " + item.getStockQuantity() + " left"));
            }
        }
        return issues;
    }
}
