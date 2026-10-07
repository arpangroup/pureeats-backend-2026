package com.pureeats.order.controller;

import com.pureeats.domain.common.response.ApiResponse;
import com.pureeats.order.dto.*;
import com.pureeats.order.service.RiderEarningsService;
import com.pureeats.user.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** The signed-in rider's own earnings, settlements, analytics and wallet ledger (DELIVERY role - see SecurityConfig's /api/v1/delivery/** rule). */
@RestController
@RequestMapping("/api/v1/delivery")
@RequiredArgsConstructor
@Tag(name = "Delivery rider earnings", description = "Earnings per trip, pending settlement, settlements, analytics")
@SecurityRequirement(name = "bearerAuth")
public class DeliveryEarningsController {

    private final RiderEarningsService riderEarningsService;

    @GetMapping("/earnings/summary")
    @Operation(summary = "Lifetime earnings plus what is pending settlement: unpaid earnings, COD cash in hand and the net amount")
    public ApiResponse<RiderEarningsSummaryResponse> summary(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.success(riderEarningsService.summary(principal.userId()));
    }

    @GetMapping("/earnings")
    @Operation(summary = "Every delivered trip as an earning, newest first (pending=true for unsettled trips only)")
    public ApiResponse<List<RiderEarningResponse>> earnings(@AuthenticationPrincipal AuthenticatedUser principal,
                                                           @RequestParam(defaultValue = "false") boolean pending) {
        return ApiResponse.success(riderEarningsService.earnings(principal.userId(), pending));
    }

    @GetMapping("/earnings/orders/{orderId}")
    @Operation(summary = "Earning breakdown for one delivered order")
    public ApiResponse<RiderEarningResponse> earning(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long orderId) {
        return ApiResponse.success(riderEarningsService.earningForOrder(principal.userId(), orderId));
    }

    @GetMapping("/earnings/analytics")
    @Operation(summary = "Earnings analytics - period=DAY|WEEK|MONTH|CUSTOM (CUSTOM needs from/to, yyyy-MM-dd): current vs previous period, chart series and breakdowns")
    public ApiResponse<RiderEarningsAnalyticsResponse> analytics(@AuthenticationPrincipal AuthenticatedUser principal,
                                                                @RequestParam(defaultValue = "DAY") String period,
                                                                @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate from,
                                                                @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate to) {
        return ApiResponse.success(riderEarningsService.analytics(principal.userId(), period, from, to));
    }

    @GetMapping("/settlements")
    @Operation(summary = "Settlements recorded for the signed-in rider, newest first")
    public ApiResponse<List<RiderSettlementResponse>> settlements(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.success(riderEarningsService.settlements(principal.userId()));
    }

    @GetMapping("/settlements/{settlementId}")
    @Operation(summary = "One settlement")
    public ApiResponse<RiderSettlementResponse> settlement(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long settlementId) {
        return ApiResponse.success(riderEarningsService.settlement(principal.userId(), settlementId));
    }

    @GetMapping("/wallet/transactions")
    @Operation(summary = "Wallet ledger with each entry linked to its order (EARNING) or settlement (SETTLEMENT)")
    public ApiResponse<List<RiderWalletTransactionResponse>> walletTransactions(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.success(riderEarningsService.walletTransactions(principal.userId()));
    }

    @PostMapping("/wallet/withdrawals")
    @Operation(summary = "Request a withdrawal from the wallet to the bank account / UPI ID on file")
    public ApiResponse<RiderSettlementResponse> requestWithdrawal(@AuthenticationPrincipal AuthenticatedUser principal,
                                                                  @RequestBody java.util.Map<String, java.math.BigDecimal> body) {
        return ApiResponse.success("Withdrawal requested", riderEarningsService.requestWithdrawal(principal.userId(), body.get("amount")));
    }
}
