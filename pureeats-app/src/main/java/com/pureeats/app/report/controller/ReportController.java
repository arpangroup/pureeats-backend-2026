package com.pureeats.app.report.controller;

import com.pureeats.app.report.dto.OrderStatusSliceRow;
import com.pureeats.app.report.dto.RevenueTrendPoint;
import com.pureeats.app.report.dto.TopItemReportRow;
import com.pureeats.app.report.dto.TopRestaurantReportRow;
import com.pureeats.app.report.dto.TopRiderReportRow;
import com.pureeats.app.report.service.ReportService;
import com.pureeats.domain.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Sales/item/delivery-partner performance reports for the admin Reports page - ADMIN or SUPER_ADMIN only. */
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@Tag(name = "Reports", description = "Sales, item and delivery-partner performance reports")
@SecurityRequirement(name = "bearerAuth")
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/api/v1/reports/top-items")
    @Operation(summary = "Top 10 most-sold items in a date range, optionally scoped to one restaurant")
    public ApiResponse<List<TopItemReportRow>> topItems(
            @RequestParam(required = false) String from, @RequestParam String to,
            @RequestParam(required = false) Long restaurantId) {
        return ApiResponse.success(reportService.topItems(from, to, restaurantId, 10));
    }

    @GetMapping("/api/v1/reports/revenue-trend")
    @Operation(summary = "Daily orders + revenue trend across a date range, optionally scoped to one restaurant")
    public ApiResponse<List<RevenueTrendPoint>> revenueTrend(
            @RequestParam(required = false) String from, @RequestParam String to,
            @RequestParam(required = false) Long restaurantId) {
        return ApiResponse.success(reportService.revenueTrend(from, to, restaurantId));
    }

    @GetMapping("/api/v1/reports/orders-by-status")
    @Operation(summary = "Order count per status in a date range, optionally scoped to one restaurant")
    public ApiResponse<List<OrderStatusSliceRow>> ordersByStatus(
            @RequestParam(required = false) String from, @RequestParam String to,
            @RequestParam(required = false) Long restaurantId) {
        return ApiResponse.success(reportService.ordersByStatus(from, to, restaurantId));
    }

    @GetMapping("/api/v1/reports/top-restaurants")
    @Operation(summary = "Top 10 restaurants by revenue in a date range")
    public ApiResponse<List<TopRestaurantReportRow>> topRestaurants(
            @RequestParam(required = false) String from, @RequestParam String to) {
        return ApiResponse.success(reportService.topRestaurants(from, to, 10));
    }

    @GetMapping("/api/v1/reports/top-riders")
    @Operation(summary = "Top 10 delivery partners by earnings in a date range")
    public ApiResponse<List<TopRiderReportRow>> topRiders(
            @RequestParam(required = false) String from, @RequestParam String to) {
        return ApiResponse.success(reportService.topRiders(from, to, 10));
    }
}
