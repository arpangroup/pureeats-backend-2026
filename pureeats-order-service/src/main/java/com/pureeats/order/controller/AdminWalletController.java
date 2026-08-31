package com.pureeats.order.controller;

import com.pureeats.domain.common.response.ApiResponse;
import com.pureeats.domain.common.response.PageResponse;
import com.pureeats.order.dto.AdminTransactionResponse;
import com.pureeats.order.dto.AdminWalletResponse;
import com.pureeats.order.dto.AdminWalletTransactionResponse;
import com.pureeats.order.dto.WalletAdjustRequest;
import com.pureeats.order.service.WalletService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Admin view/adjustment of any user's wallet - ADMIN or SUPER_ADMIN only. */
@RestController
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@Tag(name = "Admin Wallet", description = "View and adjust any user's wallet - ADMIN or SUPER_ADMIN only")
public class AdminWalletController {

    private final WalletService walletService;

    @GetMapping("/api/v1/admin/wallet")
    @Operation(summary = "Get (or lazily create) a holder's wallet")
    public ApiResponse<AdminWalletResponse> getWallet(@RequestParam String holderType, @RequestParam Long holderId) {
        log.debug("Admin fetching wallet for holderType={} holderId={}", holderType, holderId);
        return ApiResponse.success(walletService.getWalletForHolder(holderId));
    }

    @GetMapping("/api/v1/admin/wallet/transactions")
    @Operation(summary = "Platform-wide transaction ledger across every wallet, newest first")
    public ApiResponse<PageResponse<AdminTransactionResponse>> allTransactions(
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success(walletService.listAllTransactions(pageable));
    }

    @GetMapping("/api/v1/admin/wallet/{walletId}/transactions")
    @Operation(summary = "List a wallet's transactions")
    public ApiResponse<List<AdminWalletTransactionResponse>> transactions(@PathVariable Long walletId) {
        return ApiResponse.success(walletService.getTransactionsForWallet(walletId));
    }

    @PostMapping("/api/v1/admin/wallet/{walletId}/adjust")
    @Operation(summary = "Credit or debit a wallet")
    public ApiResponse<AdminWalletResponse> adjust(@PathVariable Long walletId, @Valid @RequestBody WalletAdjustRequest request) {
        log.info("Admin adjusting wallet {}: type={} amount={}", walletId, request.type(), request.amount());
        return ApiResponse.success("Wallet adjusted", walletService.adjust(walletId, request));
    }
}
