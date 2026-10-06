package com.pureeats.order.service.cartvalidation;

import com.pureeats.catalog.service.SettingSchemaService;
import com.pureeats.catalog.service.SettingValueService;
import com.pureeats.domain.enums.OrderStatusCode;
import com.pureeats.order.repository.OrderRepository;
import com.pureeats.order.service.OrderStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * "Max orders in queue": caps how many not-yet-finished orders one customer may have in flight at
 * once (Settings -> Customer Application -> Order limits -> Max orders in queue per customer, 0 =
 * unlimited). Complements {@link OrderFrequencyRule}, which only looks at how recently orders were
 * placed, not whether they're still open - a customer can stay under the rate limit and still pile
 * up a long queue of undelivered orders.
 * <p>
 * Only orders placed within the last {@link #LOOKBACK_HOURS} hours count, so a legacy/abandoned order
 * stuck in a non-terminal status (never accepted, never cancelled) can't lock a customer out forever.
 */
@Component
@RequiredArgsConstructor
public class ActiveOrderLimitRule implements CartValidationRule {

    private static final long LOOKBACK_HOURS = 24;

    private static final Set<OrderStatusCode> IN_PROGRESS = EnumSet.of(
            OrderStatusCode.PLACED, OrderStatusCode.RESTAURANT_ACCEPTED, OrderStatusCode.PREPARING,
            OrderStatusCode.READY_FOR_PICKUP, OrderStatusCode.RIDER_ASSIGNED, OrderStatusCode.PICKED_UP,
            OrderStatusCode.ON_THE_WAY);

    private final OrderRepository orderRepository;
    private final OrderStatusService orderStatusService;
    private final SettingValueService settingValueService;

    @Override
    public List<CartIssue> evaluate(CartValidationContext context) {
        if (context.userId() == null) {
            return List.of();
        }
        int max = settingValueService.getInt(SettingSchemaService.MAX_ACTIVE_ORDERS_PER_CUSTOMER, 3);
        if (max <= 0) {
            return List.of();
        }
        LocalDateTime lookbackStart = LocalDateTime.now().minusHours(LOOKBACK_HOURS);
        long inProgress = orderRepository.findByUserIdOrderByCreatedAtDesc(context.userId().intValue()).stream()
                .filter(o -> o.getCreatedAt() != null && o.getCreatedAt().isAfter(lookbackStart))
                .filter(o -> IN_PROGRESS.contains(orderStatusService.codeFor(o.getOrderstatusId())))
                .count();
        if (inProgress >= max) {
            String template = settingValueService.getString(SettingSchemaService.MAX_ACTIVE_ORDERS_MESSAGE,
                    SettingSchemaService.DEFAULT_MAX_ACTIVE_ORDERS_MESSAGE);
            return List.of(CartIssue.restaurantLevel(template.replace("{count}", String.valueOf(inProgress))));
        }
        return List.of();
    }
}
