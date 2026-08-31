package com.pureeats.app.dashboard.controller;

import com.pureeats.app.dashboard.dto.AdminDashboardResponse;
import com.pureeats.app.dashboard.dto.OwnerDashboardResponse;
import com.pureeats.app.dashboard.service.DashboardService;
import com.pureeats.catalog.service.RestaurantService;
import com.pureeats.domain.common.response.ApiResponse;
import com.pureeats.user.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Aggregate dashboard stats for admins and store owners")
public class DashboardController {

    private final DashboardService dashboardService;
    private final RestaurantService restaurantService;

    @GetMapping("/api/v1/admin/dashboard")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Platform-wide dashboard stats")
    public ApiResponse<AdminDashboardResponse> admin() {
        log.debug("Fetching admin dashboard stats");
        return ApiResponse.success(dashboardService.adminDashboard());
    }

    @GetMapping("/api/v1/store-owner/dashboard/{restaurantId}")
    @PreAuthorize("hasRole('STORE_OWNER')")
    @Operation(summary = "Dashboard stats for one of the caller's own restaurants")
    public ApiResponse<OwnerDashboardResponse> owner(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long restaurantId) {
        log.debug("Fetching owner dashboard stats for restaurant {} (requested by user {})", restaurantId, principal.userId());
        restaurantService.assertOwnership(principal.userId(), restaurantId);
        return ApiResponse.success(dashboardService.ownerDashboard(restaurantId));
    }
}
