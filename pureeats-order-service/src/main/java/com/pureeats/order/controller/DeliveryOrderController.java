package com.pureeats.order.controller;

import com.pureeats.domain.common.response.ApiResponse;
import com.pureeats.order.dto.*;
import com.pureeats.order.service.DeliveryOrderService;
import com.pureeats.user.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/delivery")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Delivery rider", description = "Rider order workflow: accept, pickup, deliver, GPS tracking")
@SecurityRequirement(name = "bearerAuth")
public class DeliveryOrderController {

    private final DeliveryOrderService deliveryOrderService;

    @GetMapping("/orders/available")
    @Operation(summary = "List orders available to be picked up for delivery")
    public ApiResponse<List<OrderSummaryResponse>> available() {
        return ApiResponse.success(deliveryOrderService.availableOrders());
    }

    @PostMapping("/orders/{orderId}/accept")
    @Operation(summary = "Accept an order for delivery")
    public ApiResponse<OrderResponse> accept(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long orderId) {
        log.info("Rider {} accepting order {} for delivery", principal.userId(), orderId);
        return ApiResponse.success("Order accepted for delivery", deliveryOrderService.acceptToDeliver(principal.userId(), orderId));
    }

    @PostMapping("/orders/{orderId}/pickup")
    @Operation(summary = "Mark an order as picked up from the restaurant")
    public ApiResponse<OrderResponse> pickup(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long orderId) {
        log.info("Rider {} marking order {} as picked up", principal.userId(), orderId);
        return ApiResponse.success("Order marked as picked up", deliveryOrderService.pickedUp(principal.userId(), orderId));
    }

    @PostMapping("/orders/{orderId}/deliver")
    @Operation(summary = "Complete delivery by verifying the customer's delivery PIN")
    public ApiResponse<OrderResponse> deliver(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long orderId,
                                               @Valid @RequestBody DeliverOrderRequest request) {
        log.info("Rider {} completing delivery for order {}", principal.userId(), orderId);
        return ApiResponse.success("Order delivered", deliveryOrderService.deliver(principal.userId(), orderId, request.deliveryPin()));
    }

    @PostMapping("/gps")
    @Operation(summary = "Report the rider's current GPS location for an order")
    public ApiResponse<Void> pingGps(@Valid @RequestBody GpsPingRequest request) {
        log.debug("GPS ping received for order {}", request.orderId());
        deliveryOrderService.recordGpsPing(request);
        return ApiResponse.success("Location updated", null);
    }

    @GetMapping("/orders/{orderId}/gps")
    @Operation(summary = "Get the rider's last known GPS location for an order")
    public ApiResponse<GpsLocationResponse> getGps(@PathVariable Long orderId) {
        return ApiResponse.success(deliveryOrderService.getGpsLocation(orderId));
    }
}
