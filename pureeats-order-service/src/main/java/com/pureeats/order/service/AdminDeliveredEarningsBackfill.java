package com.pureeats.order.service;

import com.pureeats.domain.enums.OrderStatusCode;
import com.pureeats.order.entity.OrderStatusLog;
import com.pureeats.order.repository.OrderStatusLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * One-time catch-up on startup: orders an admin marked Delivered through the old status-override path
 * (log note "Updated by admin") never had their earnings recorded - no trip record, no wallet credit for
 * the delivery partner, no restaurant payout. This records them exactly like a normal delivery.
 * Idempotent: orders that already have a trip record are skipped, so it does nothing on later restarts.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminDeliveredEarningsBackfill implements ApplicationRunner {

    /** The note the old admin status override wrote - only those deliveries skipped the earnings step. */
    static final String LEGACY_ADMIN_NOTE = "Updated by admin";

    private final OrderStatusLogRepository orderStatusLogRepository;
    private final DeliveryOrderService deliveryOrderService;

    @Value("${pureeats.backfill.admin-delivered-earnings:true}")
    private boolean enabled;

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) return;
        int recorded = 0;
        for (OrderStatusLog entry : orderStatusLogRepository.findByToStatusAndActorTypeAndNote(OrderStatusCode.DELIVERED.name(), "ADMIN", LEGACY_ADMIN_NOTE)) {
            try {
                if (deliveryOrderService.recordMissingEarnings(entry.getOrderId(), entry.getCreatedAt())) recorded++;
            } catch (Exception e) {
                log.warn("Could not record missing earnings for admin-delivered order {}: {}", entry.getOrderId(), e.getMessage());
            }
        }
        if (recorded > 0) log.info("Recorded earnings for {} order(s) an admin had marked delivered", recorded);
    }
}
