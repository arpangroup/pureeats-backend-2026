package com.pureeats.order.controller;

import com.pureeats.domain.common.response.ApiResponse;
import com.pureeats.order.dto.AdminCartSimulateRequest;
import com.pureeats.order.dto.CartValidationResponse;
import com.pureeats.order.service.cartvalidation.CartValidationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Backs the admin panel's Cart Simulator - runs the exact same {@link CartValidationService} rules
 * and pricing the real Cart page/checkout use, against an admin-picked restaurant/cart/location
 * instead of a real signed-in customer's. Read-only (same {@code validate()}-family guarantee as
 * the customer-facing endpoint - no coupon usage recorded, nothing persisted), so it's safe to run
 * arbitrarily. Gated by the existing {@code /api/v1/admin/**} ADMIN/SUPER_ADMIN rule in SecurityConfig.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/cart-simulate")
@RequiredArgsConstructor
@Tag(name = "Admin: Cart Simulator", description = "Run the real cart validation/pricing pipeline against an arbitrary cart")
public class AdminCartSimulatorController {

    private final CartValidationService cartValidationService;

    @PostMapping
    @Operation(summary = "Validate and price a simulated cart using the real backend rules")
    public ApiResponse<CartValidationResponse> simulate(@Valid @RequestBody AdminCartSimulateRequest request) {
        return ApiResponse.success(cartValidationService.simulate(
                request.restaurantId(), request.items(), request.deliveryType(),
                request.customerLat(), request.customerLng(), request.couponCode(), request.paymentMode()));
    }
}
