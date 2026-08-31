package com.pureeats.order.controller;

import com.pureeats.domain.common.response.ApiResponse;
import com.pureeats.order.service.StoreOwnerOrderService;
import com.pureeats.user.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/store-owner/restaurants/{restaurantId}/earnings")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Store owner - Earnings", description = "Restaurant earnings settlement")
@SecurityRequirement(name = "bearerAuth")
public class StoreOwnerEarningsController {

    private final StoreOwnerOrderService storeOwnerOrderService;

    @GetMapping
    @Operation(summary = "Get unsettled earnings for this restaurant")
    public ApiResponse<BigDecimal> get(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long restaurantId) {
        return ApiResponse.success(storeOwnerOrderService.unsettledEarnings(principal.userId(), restaurantId));
    }

    @PostMapping("/payout-request")
    @Operation(summary = "Request a payout of the unsettled balance")
    public ApiResponse<Void> requestPayout(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long restaurantId) {
        log.info("Store owner {} requesting payout for restaurant {}", principal.userId(), restaurantId);
        storeOwnerOrderService.requestPayout(principal.userId(), restaurantId);
        return ApiResponse.success("Payout requested", null);
    }
}
