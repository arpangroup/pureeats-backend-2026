package com.pureeats.order.controller;

import com.pureeats.domain.common.response.ApiResponse;
import com.pureeats.domain.common.response.PageResponse;
import com.pureeats.order.dto.AdminOrderSummaryResponse;
import com.pureeats.order.dto.OrderResponse;
import com.pureeats.order.dto.OrderStatusResponse;
import com.pureeats.order.service.OrderService;
import com.pureeats.order.service.OrderStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Admin-panel order directory - every order across every customer/restaurant, not just the caller's own. */
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@Tag(name = "Admin Orders", description = "Read-only order directory - ADMIN or SUPER_ADMIN only")
public class AdminOrderController {

    private final OrderService orderService;
    private final OrderStatusService orderStatusService;

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
}
