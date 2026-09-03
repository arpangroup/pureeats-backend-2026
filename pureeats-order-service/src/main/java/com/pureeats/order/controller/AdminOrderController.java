package com.pureeats.order.controller;

import com.pureeats.domain.common.response.ApiResponse;
import com.pureeats.domain.common.response.PageResponse;
import com.pureeats.domain.enums.OrderStatusCode;
import com.pureeats.order.dto.AdminOrderSummaryResponse;
import com.pureeats.order.dto.AssignDriverRequest;
import com.pureeats.order.dto.OrderResponse;
import com.pureeats.order.dto.OrderStatusLogResponse;
import com.pureeats.order.dto.OrderStatusResponse;
import com.pureeats.order.dto.OrderTimelineResponse;
import com.pureeats.order.dto.UpdateOrderStatusRequest;
import com.pureeats.order.service.DeliveryOrderService;
import com.pureeats.order.service.OrderService;
import com.pureeats.order.service.OrderStatusLogService;
import com.pureeats.order.service.OrderStatusService;
import com.pureeats.user.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Admin-panel order directory - every order across every customer/restaurant, not just the caller's own. */
@RestController
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@Tag(name = "Admin Orders", description = "Order directory + status override - ADMIN or SUPER_ADMIN only")
public class AdminOrderController {

    private final OrderService orderService;
    private final OrderStatusService orderStatusService;
    private final OrderStatusLogService orderStatusLogService;
    private final DeliveryOrderService deliveryOrderService;

    @GetMapping("/api/v1/admin/orders")
    @Operation(summary = "List every order, optionally filtered by restaurant, status, or a uniqueOrderId search")
    public ApiResponse<PageResponse<AdminOrderSummaryResponse>> list(
            @RequestParam(required = false) Long restaurantId,
            @RequestParam(required = false) Integer statusId,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success(orderService.listPaged(restaurantId, statusId, search, pageable));
    }

    @GetMapping("/api/v1/admin/orders/{id}")
    @Operation(summary = "Get an order's full detail, regardless of who placed it")
    public ApiResponse<OrderResponse> getById(@PathVariable Long id) {
        return ApiResponse.success(orderService.getOrderForAdmin(id));
    }

    @GetMapping("/api/v1/admin/order-statuses")
    @Operation(summary = "List every order status lookup row (id + name), for filter dropdowns")
    public ApiResponse<List<OrderStatusResponse>> listStatuses() {
        return ApiResponse.success(orderStatusService.listAll());
    }

    @PatchMapping("/api/v1/admin/orders/{id}/status")
    @Operation(summary = "Override an order's status (validated against the same transition graph the UI uses to grey out illegal choices)")
    public ApiResponse<OrderResponse> updateStatus(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long id,
                                                    @Valid @RequestBody UpdateOrderStatusRequest request) {
        OrderStatusCode toStatus = OrderStatusCode.fromValue(request.toStatus());
        log.info("Admin {} overriding status of order {} to {}", principal.userId(), id, toStatus);
        return ApiResponse.success("Order status updated", orderService.adminUpdateStatus(principal.userId(), id, toStatus));
    }

    @GetMapping("/api/v1/admin/orders/{id}/log")
    @Operation(summary = "Get an order's full status-transition journey - who changed what, and when")
    public ApiResponse<List<OrderStatusLogResponse>> journey(@PathVariable Long id) {
        return ApiResponse.success(orderStatusLogService.journey(id));
    }

    @GetMapping("/api/v1/admin/orders/{id}/timeline")
    @Operation(summary = "Get the compact milestone timeline for an order")
    public ApiResponse<OrderTimelineResponse> timeline(@PathVariable Long id) {
        return ApiResponse.success(orderStatusLogService.timeline(id));
    }

    @PostMapping("/api/v1/admin/orders/{id}/assign-driver")
    @Operation(summary = "Assign a specific delivery partner to this order directly")
    public ApiResponse<OrderResponse> assignDriver(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long id,
                                                    @Valid @RequestBody AssignDriverRequest request) {
        log.info("Admin {} assigning rider {} to order {}", principal.userId(), request.riderUserId(), id);
        return ApiResponse.success("Driver assigned", deliveryOrderService.assignDriverAsAdmin(principal.userId(), id, request.riderUserId()));
    }
}
