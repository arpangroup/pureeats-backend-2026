package com.pureeats.app.deliveryguy.controller;

import com.pureeats.app.deliveryguy.dto.AdminDeliveryGuyRequest;
import com.pureeats.app.deliveryguy.dto.AdminDeliveryGuyResponse;
import com.pureeats.app.deliveryguy.dto.TripDetailResponse;
import com.pureeats.app.deliveryguy.service.AdminDeliveryGuyService;
import com.pureeats.domain.common.response.ApiResponse;
import com.pureeats.domain.common.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Admin CRUD for delivery partners - ADMIN or SUPER_ADMIN only. */
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@Tag(name = "Admin Delivery Partners", description = "Delivery-partner directory and management - ADMIN or SUPER_ADMIN only")
public class AdminDeliveryGuyController {

    private final AdminDeliveryGuyService deliveryGuyService;

    @GetMapping("/api/v1/admin/delivery-guys")
    public ApiResponse<PageResponse<AdminDeliveryGuyResponse>> list(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success(deliveryGuyService.listPaged(search, pageable));
    }

    @GetMapping("/api/v1/admin/delivery-guys/{id}")
    public ApiResponse<AdminDeliveryGuyResponse> getById(@PathVariable Long id) {
        return ApiResponse.success(deliveryGuyService.getById(id));
    }

    @PostMapping("/api/v1/admin/delivery-guys")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a delivery partner - provisions a login-capable user account and grants the DELIVERY role")
    public ApiResponse<AdminDeliveryGuyResponse> create(@RequestBody AdminDeliveryGuyRequest request) {
        return ApiResponse.success("Delivery partner created", deliveryGuyService.create(request));
    }

    @PutMapping("/api/v1/admin/delivery-guys/{id}")
    public ApiResponse<AdminDeliveryGuyResponse> update(@PathVariable Long id, @RequestBody AdminDeliveryGuyRequest request) {
        return ApiResponse.success("Delivery partner updated", deliveryGuyService.update(id, request));
    }

    @DeleteMapping("/api/v1/admin/delivery-guys/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        deliveryGuyService.delete(id);
        return ApiResponse.success("Delivery partner removed", null);
    }

    @GetMapping("/api/v1/admin/delivery-guys/{id}/restaurants")
    public ApiResponse<List<Long>> assignedRestaurants(@PathVariable Long id) {
        return ApiResponse.success(deliveryGuyService.assignedRestaurantIds(id));
    }

    @PutMapping("/api/v1/admin/delivery-guys/{id}/restaurants")
    public ApiResponse<List<Long>> updateAssignedRestaurants(@PathVariable Long id, @RequestBody Map<String, List<Long>> body) {
        return ApiResponse.success(deliveryGuyService.updateAssignedRestaurants(id, body.get("restaurantIds")));
    }

    @GetMapping("/api/v1/admin/delivery-guys/{riderUserId}/earnings")
    @Operation(summary = "Per-order earnings for a rider, identified by their User id")
    public ApiResponse<List<TripDetailResponse>> earnings(@PathVariable Long riderUserId) {
        return ApiResponse.success(deliveryGuyService.earningsForRider(riderUserId));
    }
}
