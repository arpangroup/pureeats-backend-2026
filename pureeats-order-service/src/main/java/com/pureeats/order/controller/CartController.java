package com.pureeats.order.controller;

import com.pureeats.domain.common.response.ApiResponse;
import com.pureeats.order.dto.CartValidationRequest;
import com.pureeats.order.dto.CartValidationResponse;
import com.pureeats.order.service.cartvalidation.CartValidationService;
import com.pureeats.user.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Live pre-checkout validation for the Cart page: which items are still available, whether the
 * coupon still applies, and what it would actually cost right now (address included, so changing
 * the delivery address and coming back here recalculates delivery charge). Authenticated only -
 * guests use the lighter public {@code /pricing/delivery-quote} + {@code /geo/ip-location} instead,
 * since they have no saved address or order history to validate against.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Cart", description = "Live cart validation and pricing")
@SecurityRequirement(name = "bearerAuth")
public class CartController {

    private final CartValidationService cartValidationService;

    @PostMapping("/api/v1/cart/validate")
    @Operation(summary = "Check item/coupon availability and compute current pricing for a cart")
    public ApiResponse<CartValidationResponse> validate(@AuthenticationPrincipal AuthenticatedUser principal,
                                                          @Valid @RequestBody CartValidationRequest request) {
        return ApiResponse.success(cartValidationService.validate(request, principal.userId()));
    }
}
