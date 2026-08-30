package com.pureeats.app.dashboard.dto;

import java.math.BigDecimal;
import java.util.List;

public record AdminDashboardResponse(
        long totalOrders,
        BigDecimal totalRevenue,
        long activeRestaurants,
        long totalRestaurants,
        long totalCustomers,
        long onlineRiders,
        long totalRiders,
        double avgRating,
        List<OrderStatusCountResponse> ordersByStatus,
        List<TrendPointResponse> trend,
        List<TopRestaurantResponse> topRestaurants
) {
}
