package com.pureeats.catalog.controller;

import com.pureeats.catalog.dto.CouponApplyRequest;
import com.pureeats.catalog.dto.CouponApplyResponse;
import com.pureeats.catalog.dto.CouponCreateRequest;
import com.pureeats.catalog.dto.CouponResponse;
import com.pureeats.catalog.service.CouponService;
import com.pureeats.domain.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Coupons", description = "Coupon discovery, preview and store-owner creation")
public class CouponController {

    private final CouponService couponService;

    @GetMapping("/api/v1/coupons")
    @Operation(summary = "List coupons available for a restaurant (includes global coupons)")
    public ApiResponse<List<CouponResponse>> list(@RequestParam Integer restaurantId) {
        return ApiResponse.success(couponService.listAvailable(restaurantId));
    }

    @PostMapping("/api/v1/coupons/preview")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Validate a coupon code and preview the discount for a given order amount")
    public ApiResponse<CouponApplyResponse> preview(@Valid @RequestBody CouponApplyRequest request) {
        return ApiResponse.success(couponService.preview(request));
    }

    @PostMapping("/api/v1/store-owner/coupons")
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Create a coupon for your restaurant (or a global one)")
    public ApiResponse<CouponResponse> create(@Valid @RequestBody CouponCreateRequest request) {
        return ApiResponse.success("Coupon created", couponService.create(request));
    }
}
