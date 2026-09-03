package com.pureeats.order.service.cartvalidation;

import com.pureeats.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Simple anti-fraud/rate-limit rule: blocks a customer from placing yet another order within a
 * short rolling window, alongside the existing first-order-only coupon check. Only meaningful once
 * a caller is known (userId) - the guest cart-preview path never sees this rule fire.
 */
@Component
@RequiredArgsConstructor
public class OrderFrequencyRule implements CartValidationRule {

    private final OrderRepository orderRepository;

    @Value("${pureeats.order.rate-limit.window-minutes:10}")
    private int windowMinutes;

    @Value("${pureeats.order.rate-limit.max-orders:3}")
    private int maxOrders;

    @Override
    public List<CartIssue> evaluate(CartValidationContext context) {
        if (context.userId() == null) {
            return List.of();
        }
        LocalDateTime windowStart = LocalDateTime.now().minusMinutes(windowMinutes);
        long recentCount = orderRepository.findByUserIdOrderByCreatedAtDesc(context.userId().intValue()).stream()
                .filter(o -> o.getCreatedAt() != null && o.getCreatedAt().isAfter(windowStart))
                .count();
        if (recentCount >= maxOrders) {
            return List.of(CartIssue.restaurantLevel("You've placed " + recentCount + " orders in the last "
                    + windowMinutes + " minutes - please wait a little before placing another."));
        }
        return List.of();
    }
}
