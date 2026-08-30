package com.pureeats.app.dashboard.dto;

import java.math.BigDecimal;
import java.util.List;

public record OwnerDashboardResponse(
        long totalOrders,
        BigDecimal totalRevenue,
        long pendingOrders,
        double avgRating,
        List<TrendPointResponse> trend
) {
}
