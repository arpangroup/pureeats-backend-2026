package com.pureeats.order.service.cartvalidation;

import java.util.List;

/**
 * One independent availability check (restaurant status, item status, stock, ...). Implementations
 * are Spring beans, auto-collected by {@code CartValidationService} the same way
 * {@code CouponService} collects its {@code DiscountCalculator}s — adding a new rule is just adding
 * a new class, never an edit to an if/else chain.
 * <p>
 * Rules never throw and never short-circuit each other: {@code CartValidationService} runs every
 * rule and merges the results, so a customer with one unavailable item among five still sees all
 * five evaluated, not just the first failure.
 */
public interface CartValidationRule {
    List<CartIssue> evaluate(CartValidationContext context);
}
