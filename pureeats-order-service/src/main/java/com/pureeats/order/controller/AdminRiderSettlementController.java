package com.pureeats.order.controller;

import com.pureeats.domain.common.response.ApiResponse;
import com.pureeats.order.dto.*;
import com.pureeats.order.service.RiderEarningsService;
import com.pureeats.user.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Admin side of rider settlement - path variable is the rider's USER id, same as /admin/delivery-guys/{riderUserId}/earnings. */
@Slf4j
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@Tag(name = "Admin rider settlements", description = "Pending rider earnings / COD cash and recording settlements")
public class AdminRiderSettlementController {

    private final RiderEarningsService riderEarningsService;

    @GetMapping("/api/v1/admin/delivery-guys/{riderUserId}/settlement-summary")
    @Operation(summary = "What a rider has pending: unpaid earnings, COD cash held, net amount and direction")
    public ApiResponse<RiderEarningsSummaryResponse> summary(@PathVariable Long riderUserId) {
        return ApiResponse.success(riderEarningsService.summary(riderUserId));
    }

    @GetMapping("/api/v1/admin/delivery-guys/{riderUserId}/pending-earnings")
    @Operation(summary = "The rider's unsettled trips - exactly what the next settlement will cover")
    public ApiResponse<List<RiderEarningResponse>> pending(@PathVariable Long riderUserId) {
        return ApiResponse.success(riderEarningsService.earnings(riderUserId, true));
    }

    @GetMapping("/api/v1/admin/delivery-guys/{riderUserId}/settlements")
    @Operation(summary = "Settlement history for a rider, newest first")
    public ApiResponse<List<RiderSettlementResponse>> settlements(@PathVariable Long riderUserId) {
        return ApiResponse.success(riderEarningsService.settlements(riderUserId));
    }

    @PostMapping("/api/v1/admin/delivery-guys/{riderUserId}/settlements")
    @Operation(summary = "Settle everything the rider has pending (nets earnings against COD cash held)")
    public ApiResponse<RiderSettlementResponse> settle(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long riderUserId,
                                                       @Valid @RequestBody(required = false) SettleRiderRequest request) {
        log.info("Admin {} settling rider {}", principal.userId(), riderUserId);
        return ApiResponse.success("Settlement recorded", riderEarningsService.settle(principal.userId(), riderUserId, request));
    }
}
