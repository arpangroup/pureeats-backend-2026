package com.pureeats.catalog.controller;

import com.pureeats.catalog.dto.CouponApplyRequest;
import com.pureeats.catalog.dto.CouponApplyResponse;
import com.pureeats.catalog.dto.CouponCreateRequest;
import com.pureeats.catalog.dto.CouponResponse;
import com.pureeats.catalog.dto.CouponUpdateRequest;
import com.pureeats.catalog.service.CouponService;
import com.pureeats.catalog.service.RestaurantService;
import com.pureeats.domain.common.response.ApiResponse;
import com.pureeats.domain.common.response.PageResponse;
import com.pureeats.user.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Coupons", description = "Coupon discovery, preview and store-owner creation")
public class CouponController {

    private final CouponService couponService;
    private final RestaurantService restaurantService;

    @GetMapping("/api/v1/coupons")
    @Operation(summary = "List coupons available for a restaurant (includes global coupons)")
    public ApiResponse<List<CouponResponse>> list(@RequestParam Integer restaurantId) {
        log.debug("Listing available coupons for restaurant {}", restaurantId);
        return ApiResponse.success(couponService.listAvailable(restaurantId));
    }

    @PostMapping("/api/v1/coupons/preview")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Validate a coupon code and preview the discount for a given order amount")
    public ApiResponse<CouponApplyResponse> preview(@Valid @RequestBody CouponApplyRequest request) {
        log.debug("Previewing coupon '{}' for restaurant {}", request.code(), request.restaurantId());
        return ApiResponse.success(couponService.preview(request));
    }

    @GetMapping("/api/v1/store-owner/coupons")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "List every coupon (any status) scoped to one of your restaurants")
    public ApiResponse<PageResponse<CouponResponse>> listOwned(
            @AuthenticationPrincipal AuthenticatedUser principal, @RequestParam Integer restaurantId,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        log.debug("Owner {}: list coupons for restaurant {}", principal.userId(), restaurantId);
        restaurantService.assertOwnership(principal.userId(), restaurantId.longValue());
        return ApiResponse.success(couponService.listForRestaurant(restaurantId, search, pageable));
    }

    @PostMapping("/api/v1/store-owner/coupons")
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Create a coupon for your restaurant (or a global one)")
    public ApiResponse<CouponResponse> create(@AuthenticationPrincipal AuthenticatedUser principal, @Valid @RequestBody CouponCreateRequest request) {
        log.debug("Owner {}: create coupon '{}'", principal.userId(), request.code());
        return ApiResponse.success("Coupon created", couponService.create(request, principal.userId()));
    }

    @PutMapping("/api/v1/store-owner/coupons/{id}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Update a coupon you created")
    public ApiResponse<CouponResponse> update(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long id,
                                               @Valid @RequestBody CouponUpdateRequest request) {
        log.debug("Owner {}: update coupon {}", principal.userId(), id);
        return ApiResponse.success("Coupon updated", couponService.updateAsOwner(principal.userId(), id, request));
    }

    @DeleteMapping("/api/v1/store-owner/coupons/{id}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Delete a coupon - only the store owner who created it may delete it")
    public ApiResponse<Void> delete(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long id) {
        log.debug("Owner {}: delete coupon {}", principal.userId(), id);
        couponService.deleteAsOwner(principal.userId(), id);
        return ApiResponse.success("Coupon deleted", null);
    }
}
