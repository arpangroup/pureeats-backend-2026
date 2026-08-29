package com.pureeats.catalog.controller;

import com.pureeats.catalog.dto.CouponResponse;
import com.pureeats.catalog.service.CouponService;
import com.pureeats.domain.common.response.ApiResponse;
import com.pureeats.domain.common.response.PageResponse;
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

/** Admin-panel coupon directory - every coupon, not just the ones valid for one restaurant. */
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@Tag(name = "Admin Coupons", description = "Read-only coupon directory - ADMIN or SUPER_ADMIN only")
public class AdminCouponController {

    private final CouponService couponService;

    @GetMapping("/api/v1/admin/coupons")
    @Operation(summary = "List every coupon, optionally filtered by a name/code search")
    public ApiResponse<PageResponse<CouponResponse>> list(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success(couponService.listPaged(search, pageable));
    }

    @GetMapping("/api/v1/admin/coupons/{id}")
    @Operation(summary = "Get a coupon's detail")
    public ApiResponse<CouponResponse> getById(@PathVariable Long id) {
        return ApiResponse.success(couponService.getById(id));
    }
}
