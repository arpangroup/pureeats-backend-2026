package com.pureeats.order.controller;

import com.pureeats.domain.common.response.ApiResponse;
import com.pureeats.order.dto.CreateRazorpayOrderRequest;
import com.pureeats.order.dto.RazorpayOrderResponse;
import com.pureeats.order.service.payment.RazorpayOrderInfo;
import com.pureeats.order.service.payment.RazorpayService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Step one of the Razorpay checkout flow — creating the order Razorpay's Checkout widget opens
 * against. Step two (verifying the payment and actually placing the order) happens inside the
 * existing {@code POST /orders} via OrderService#placeOrder, not here — see
 * docs/location-resolution/README.md's sibling note in the customer app for the full flow (or
 * RazorpayService's class doc for the short version).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/payments/razorpay")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Razorpay order creation for checkout")
@SecurityRequirement(name = "bearerAuth")
public class RazorpayController {

    private final RazorpayService razorpayService;

    @PostMapping("/orders")
    @Operation(summary = "Create a Razorpay order for the given amount, to open the Checkout widget against")
    public ApiResponse<RazorpayOrderResponse> createOrder(@AuthenticationPrincipal AuthenticatedUser principal,
                                                            @Valid @RequestBody CreateRazorpayOrderRequest request) {
        log.info("User {} creating Razorpay order for amount {}", principal.userId(), request.amount());
        RazorpayOrderInfo info = razorpayService.createOrder(request.amount(), "user-" + principal.userId() + "-" + System.currentTimeMillis());
        return ApiResponse.success(new RazorpayOrderResponse(info.razorpayOrderId(), info.keyId(), info.amountPaise(), info.currency()));
    }
}
