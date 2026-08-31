package com.pureeats.order.controller;

import com.pureeats.domain.common.response.ApiResponse;
import com.pureeats.order.dto.WalletBalanceResponse;
import com.pureeats.order.dto.WalletTransactionResponse;
import com.pureeats.order.service.WalletService;
import com.pureeats.user.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users/me/wallet")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Wallet", description = "The signed-in user's wallet balance and transaction history")
@SecurityRequirement(name = "bearerAuth")
public class WalletController {

    private final WalletService walletService;

    @GetMapping
    @Operation(summary = "Get wallet balance")
    public ApiResponse<WalletBalanceResponse> balance(@AuthenticationPrincipal AuthenticatedUser principal) {
        log.debug("Fetching wallet balance for user {}", principal.userId());
        return ApiResponse.success(walletService.getBalance(principal.userId()));
    }

    @GetMapping("/transactions")
    @Operation(summary = "List wallet transactions")
    public ApiResponse<List<WalletTransactionResponse>> transactions(@AuthenticationPrincipal AuthenticatedUser principal) {
        log.debug("Fetching wallet transactions for user {}", principal.userId());
        return ApiResponse.success(walletService.getTransactions(principal.userId()));
    }
}
