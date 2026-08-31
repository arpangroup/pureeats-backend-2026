package com.pureeats.order.controller;

import com.pureeats.domain.common.response.ApiResponse;
import com.pureeats.order.dto.OrderResponse;
import com.pureeats.order.dto.OrderSummaryResponse;
import com.pureeats.order.service.StoreOwnerOrderService;
import com.pureeats.user.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/store-owner/restaurants/{restaurantId}/orders")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Store owner - Orders", description = "Order workflow for a restaurant owner")
@SecurityRequirement(name = "bearerAuth")
public class StoreOwnerOrderController {

    private final StoreOwnerOrderService storeOwnerOrderService;

    @GetMapping("/new")
    @Operation(summary = "List newly placed orders awaiting acceptance")
    public ApiResponse<List<OrderSummaryResponse>> newOrders(@AuthenticationPrincipal AuthenticatedUser principal,
                                                              @PathVariable Long restaurantId) {
        return ApiResponse.success(storeOwnerOrderService.newOrders(principal.userId(), restaurantId));
    }

    @GetMapping("/running")
    @Operation(summary = "List orders currently in progress")
    public ApiResponse<List<OrderSummaryResponse>> runningOrders(@AuthenticationPrincipal AuthenticatedUser principal,
                                                                  @PathVariable Long restaurantId) {
        return ApiResponse.success(storeOwnerOrderService.runningOrders(principal.userId(), restaurantId));
    }

    @PostMapping("/{orderId}/accept")
    @Operation(summary = "Accept a newly placed order")
    public ApiResponse<OrderResponse> accept(@AuthenticationPrincipal AuthenticatedUser principal,
                                              @PathVariable Long restaurantId, @PathVariable Long orderId) {
        log.info("Store owner {} accepting order {} for restaurant {}", principal.userId(), orderId, restaurantId);
        return ApiResponse.success("Order accepted", storeOwnerOrderService.accept(principal.userId(), orderId));
    }

    @PostMapping("/{orderId}/ready")
    @Operation(summary = "Mark an order ready for pickup")
    public ApiResponse<OrderResponse> ready(@AuthenticationPrincipal AuthenticatedUser principal,
                                             @PathVariable Long restaurantId, @PathVariable Long orderId) {
        log.info("Store owner {} marking order {} ready for pickup", principal.userId(), orderId);
        return ApiResponse.success("Order marked ready", storeOwnerOrderService.markReady(principal.userId(), orderId));
    }

    @PostMapping("/{orderId}/self-pickup-complete")
    @Operation(summary = "Mark a self-pickup order as completed")
    public ApiResponse<OrderResponse> selfPickupComplete(@AuthenticationPrincipal AuthenticatedUser principal,
                                                          @PathVariable Long restaurantId, @PathVariable Long orderId) {
        log.info("Store owner {} marking order {} self-pickup completed", principal.userId(), orderId);
        return ApiResponse.success("Order marked as picked up", storeOwnerOrderService.markSelfPickupCompleted(principal.userId(), orderId));
    }

    @PostMapping("/{orderId}/cancel")
    @Operation(summary = "Cancel an order")
    public ApiResponse<OrderResponse> cancel(@AuthenticationPrincipal AuthenticatedUser principal,
                                              @PathVariable Long restaurantId, @PathVariable Long orderId) {
        log.info("Store owner {} cancelling order {}", principal.userId(), orderId);
        return ApiResponse.success("Order cancelled", storeOwnerOrderService.cancel(principal.userId(), orderId));
    }
}
