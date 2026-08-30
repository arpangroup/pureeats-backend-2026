package com.pureeats.order.controller;

import com.pureeats.domain.common.response.ApiResponse;
import com.pureeats.order.dto.DeliverOrderRequest;
import com.pureeats.order.dto.OrderResponse;
import com.pureeats.order.dto.OrderSummaryResponse;
import com.pureeats.order.dto.OrderTimelineResponse;
import com.pureeats.order.dto.PlaceOrderRequest;
import com.pureeats.order.service.DeliveryOrderService;
import com.pureeats.order.service.OrderService;
import com.pureeats.order.service.OrderStatusLogService;
import com.pureeats.user.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Customer-facing order placement and history")
@SecurityRequirement(name = "bearerAuth")
public class OrderController {

    private final OrderService orderService;
    private final DeliveryOrderService deliveryOrderService;
    private final OrderStatusLogService orderStatusLogService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Place a new order")
    public ApiResponse<OrderResponse> place(@AuthenticationPrincipal AuthenticatedUser principal,
                                             @Valid @RequestBody PlaceOrderRequest request) {
        return ApiResponse.success("Order placed successfully", orderService.placeOrder(principal.userId(), request));
    }

    @GetMapping
    @Operation(summary = "List the signed-in user's orders")
    public ApiResponse<List<OrderSummaryResponse>> myOrders(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.success(orderService.myOrders(principal.userId()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get order details")
    public ApiResponse<OrderResponse> get(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long id) {
        return ApiResponse.success(orderService.getOrder(principal.userId(), id));
    }

    @GetMapping("/{id}/timeline")
    @Operation(summary = "Get the compact milestone timeline for an order you placed")
    public ApiResponse<OrderTimelineResponse> timeline(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long id) {
        orderService.getOrder(principal.userId(), id);
        return ApiResponse.success(orderStatusLogService.timeline(id));
    }

    @PatchMapping("/{id}/cancel")
    @Operation(summary = "Cancel an order you placed")
    public ApiResponse<Void> cancel(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long id) {
        orderService.cancelOrder(principal.userId(), id);
        return ApiResponse.success("Order cancelled", null);
    }

    @PatchMapping("/{id}/confirm-delivery")
    @Operation(summary = "Confirm delivery yourself by providing the delivery PIN")
    public ApiResponse<OrderResponse> confirmDelivery(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long id,
                                                        @Valid @RequestBody DeliverOrderRequest request) {
        return ApiResponse.success("Delivery confirmed", deliveryOrderService.customerConfirmDelivery(principal.userId(), id, request.deliveryPin()));
    }
}
