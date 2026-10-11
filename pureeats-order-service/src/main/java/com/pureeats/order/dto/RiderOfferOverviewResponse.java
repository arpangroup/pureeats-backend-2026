package com.pureeats.order.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * The admin's Partner alerts page: the re-offer scheduler's settings and runs, and every order still waiting for a
 * delivery partner with who was alerted / dismissed and when its next alert is due.
 */
public record RiderOfferOverviewResponse(
        boolean enabled,
        int intervalMinutes,
        String mode,
        int maxOffers,
        String dispatchMode,
        LocalDateTime lastRunAt,
        /** About a minute after the last run (the scheduler checks every minute). */
        LocalDateTime nextRunAt,
        List<Run> recentRuns,
        List<WaitingOrder> waitingOrders
) {
    public record Run(LocalDateTime ranAt, boolean enabled, int waitingOrders, int reoffered, int partnersAlerted, int adminAlerts, List<String> reofferedOrders) {
    }

    public record WaitingOrder(Long orderId, String uniqueOrderId, String restaurantName, String status, BigDecimal payable,
                               String paymentMode, LocalDateTime placedAt, RiderOfferSummaryResponse alerts) {
    }
}
