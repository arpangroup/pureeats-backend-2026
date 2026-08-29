package com.pureeats.catalog.controller;

import com.pureeats.catalog.dto.RestaurantCreateRequest;
import com.pureeats.catalog.dto.RestaurantDetailResponse;
import com.pureeats.catalog.dto.RestaurantSummaryResponse;
import com.pureeats.catalog.dto.RestaurantUpdateRequest;
import com.pureeats.catalog.service.RestaurantService;
import com.pureeats.domain.common.response.ApiResponse;
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
@RequestMapping("/api/v1/store-owner/restaurants")
@RequiredArgsConstructor
@Tag(name = "Store owner - Restaurants", description = "Restaurant-owner CRUD over their own restaurants")
@SecurityRequirement(name = "bearerAuth")
public class StoreOwnerRestaurantController {

    private final RestaurantService restaurantService;

    @GetMapping
    @Operation(summary = "List restaurants owned by the signed-in owner")
    public ApiResponse<List<RestaurantSummaryResponse>> myRestaurants(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.success(restaurantService.listOwnedBy(principal.userId()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a new restaurant (pending admin acceptance)")
    public ApiResponse<RestaurantDetailResponse> create(@AuthenticationPrincipal AuthenticatedUser principal,
                                                         @Valid @RequestBody RestaurantCreateRequest request) {
        return ApiResponse.success("Restaurant created (awaiting admin approval) - log in again to receive a RESTAURANT_OWNER-role token",
                restaurantService.create(principal.userId(), request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a restaurant you own")
    public ApiResponse<RestaurantDetailResponse> update(@AuthenticationPrincipal AuthenticatedUser principal,
                                                         @PathVariable Long id,
                                                         @Valid @RequestBody RestaurantUpdateRequest request) {
        return ApiResponse.success("Restaurant updated", restaurantService.update(principal.userId(), id, request));
    }

    @PatchMapping("/{id}/enable")
    @Operation(summary = "Re-enable a restaurant you own")
    public ApiResponse<Void> enable(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long id) {
        restaurantService.setEnabled(principal.userId(), id, true);
        return ApiResponse.success("Restaurant enabled", null);
    }

    @PatchMapping("/{id}/disable")
    @Operation(summary = "Disable a restaurant you own")
    public ApiResponse<Void> disable(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long id) {
        restaurantService.setEnabled(principal.userId(), id, false);
        return ApiResponse.success("Restaurant disabled", null);
    }
}
