package com.pureeats.catalog.controller;

import com.pureeats.catalog.dto.PaymentGatewayResponse;
import com.pureeats.catalog.dto.PaymentGatewayToggleRequest;
import com.pureeats.catalog.service.ContentService;
import com.pureeats.domain.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * ADMIN/SUPER_ADMIN-only management of the {@code payment_gateways} list the Settings page's
 * "Payment gateways" panel shows - unlike the public {@code GET /payment-gateways} (active-only,
 * read-only, for the customer app to consume), this returns every row so a disabled gateway can be
 * found and re-enabled, and lets an admin actually flip {@code isActive}.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/payment-gateways")
@RequiredArgsConstructor
@Tag(name = "Admin: Payment gateways", description = "Manage which payment gateways the customer app offers")
public class AdminPaymentGatewayController {

    private final ContentService contentService;

    @GetMapping
    @Operation(summary = "List every payment gateway (active and inactive)")
    public ApiResponse<List<PaymentGatewayResponse>> list() {
        return ApiResponse.success(contentService.listAllPaymentGatewaysForAdmin());
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Enable or disable a payment gateway")
    public ApiResponse<PaymentGatewayResponse> toggle(@PathVariable Long id, @Valid @RequestBody PaymentGatewayToggleRequest request) {
        log.info("Setting payment gateway {} active={}", id, request.isActive());
        return ApiResponse.success(contentService.setPaymentGatewayActive(id, request.isActive()));
    }
}
