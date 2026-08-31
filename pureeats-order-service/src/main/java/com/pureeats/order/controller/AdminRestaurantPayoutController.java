package com.pureeats.order.controller;

import com.pureeats.domain.common.response.ApiResponse;
import com.pureeats.domain.common.response.PageResponse;
import com.pureeats.order.dto.AdminRestaurantPayoutResponse;
import com.pureeats.order.dto.UpdatePayoutStatusRequest;
import com.pureeats.order.service.RestaurantPayoutService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Admin settlement of store payout requests - ADMIN or SUPER_ADMIN only. */
@RestController
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@Tag(name = "Admin Restaurant Payouts", description = "Store payout directory and settlement - ADMIN or SUPER_ADMIN only")
public class AdminRestaurantPayoutController {

    private final RestaurantPayoutService restaurantPayoutService;

    @GetMapping("/api/v1/admin/restaurant-payouts")
    @Operation(summary = "List every payout request, newest first")
    public ApiResponse<PageResponse<AdminRestaurantPayoutResponse>> list(
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success(restaurantPayoutService.listPaged(pageable));
    }

    @GetMapping("/api/v1/admin/restaurant-payouts/{id}")
    @Operation(summary = "Get a payout's detail")
    public ApiResponse<AdminRestaurantPayoutResponse> getById(@PathVariable Long id) {
        return ApiResponse.success(restaurantPayoutService.getById(id));
    }

    @PatchMapping("/api/v1/admin/restaurant-payouts/{id}/status")
    @Operation(summary = "Update a payout's status (e.g. mark paid or rejected)")
    public ApiResponse<AdminRestaurantPayoutResponse> updateStatus(@PathVariable Long id, @Valid @RequestBody UpdatePayoutStatusRequest request) {
        log.info("Updating payout {} status to {}", id, request.status());
        return ApiResponse.success("Payout updated", restaurantPayoutService.updateStatus(id, request.status()));
    }
}
