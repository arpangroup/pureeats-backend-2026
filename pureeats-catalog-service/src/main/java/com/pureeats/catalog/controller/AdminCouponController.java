package com.pureeats.catalog.controller;

import com.pureeats.catalog.dto.CouponCreateRequest;
import com.pureeats.catalog.dto.CouponResponse;
import com.pureeats.catalog.dto.CouponUpdateRequest;
import com.pureeats.catalog.dto.CouponUsageResponse;
import com.pureeats.catalog.service.CouponService;
import com.pureeats.domain.common.response.ApiResponse;
import com.pureeats.domain.common.response.PageResponse;
import com.pureeats.user.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

/** Admin-panel coupon directory - every coupon, not just the ones valid for one restaurant. */
@Slf4j
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@Tag(name = "Admin Coupons", description = "Coupon directory and management - ADMIN or SUPER_ADMIN only")
public class AdminCouponController {

    private final CouponService couponService;

    @GetMapping("/api/v1/admin/coupons")
    @Operation(summary = "List every coupon, optionally filtered by a name/code search")
    public ApiResponse<PageResponse<CouponResponse>> list(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        log.debug("Admin: list coupons, search '{}' page {}", search, pageable.getPageNumber());
        return ApiResponse.success(couponService.listPaged(search, pageable));
    }

    @GetMapping("/api/v1/admin/coupons/{id}")
    @Operation(summary = "Get a coupon's detail")
    public ApiResponse<CouponResponse> getById(@PathVariable Long id) {
        log.debug("Admin: get coupon {}", id);
        return ApiResponse.success(couponService.getById(id));
    }

    @PostMapping("/api/v1/admin/coupons")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a coupon (global or scoped to one restaurant)")
    public ApiResponse<CouponResponse> create(@AuthenticationPrincipal AuthenticatedUser principal, @Valid @RequestBody CouponCreateRequest request) {
        log.debug("Admin {}: create coupon '{}'", principal.userId(), request.code());
        return ApiResponse.success("Coupon created", couponService.create(request, principal.userId()));
    }

    @PutMapping("/api/v1/admin/coupons/{id}")
    @Operation(summary = "Update a coupon")
    public ApiResponse<CouponResponse> update(@PathVariable Long id, @Valid @RequestBody CouponUpdateRequest request) {
        log.debug("Admin: update coupon {}", id);
        return ApiResponse.success("Coupon updated", couponService.update(id, request));
    }

    @DeleteMapping("/api/v1/admin/coupons/{id}")
    @Operation(summary = "Delete a coupon")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        log.debug("Admin: delete coupon {}", id);
        couponService.delete(id);
        return ApiResponse.success("Coupon deleted", null);
    }

    @GetMapping("/api/v1/admin/coupons/{id}/usages")
    @Operation(summary = "List a coupon's redemption history")
    public ApiResponse<List<CouponUsageResponse>> usages(@PathVariable Long id) {
        log.debug("Admin: list usages for coupon {}", id);
        return ApiResponse.success(couponService.listUsages(id));
    }
}
