package com.pureeats.order.service;

import com.pureeats.domain.enums.OrderStatusCode;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Seeds every {@link OrderStatusCode} into the legacy {@code order_statuses} lookup table on
 * startup, so no request-time code path (including read-only ones) ever needs to lazily insert
 * a missing row via {@link OrderStatusService#idFor}.
 */
@Component
@RequiredArgsConstructor
public class OrderStatusSeeder implements ApplicationRunner {

    private final OrderStatusService orderStatusService;

    @Override
    public void run(ApplicationArguments args) {
        for (OrderStatusCode code : OrderStatusCode.values()) {
            orderStatusService.idFor(code);
        }
    }
}
