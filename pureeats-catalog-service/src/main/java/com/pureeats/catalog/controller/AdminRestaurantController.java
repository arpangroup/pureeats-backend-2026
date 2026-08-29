package com.pureeats.catalog.controller;

import com.pureeats.catalog.dto.RestaurantCategoryResponse;
import com.pureeats.catalog.dto.RestaurantDetailResponse;
import com.pureeats.catalog.dto.RestaurantSummaryResponse;
import com.pureeats.catalog.service.RestaurantCategoryService;
import com.pureeats.catalog.service.RestaurantService;
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

/** Admin-panel store directory - every restaurant regardless of active/accepted status. */
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@Tag(name = "Admin Restaurants", description = "Read-only store directory - ADMIN or SUPER_ADMIN only")
public class AdminRestaurantController {

    private final RestaurantService restaurantService;
    private final RestaurantCategoryService restaurantCategoryService;

    @GetMapping("/api/v1/admin/restaurants")
    @Operation(summary = "List every restaurant, optionally filtered by a name search")
    public ApiResponse<PageResponse<RestaurantSummaryResponse>> list(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success(restaurantService.listPaged(search, pageable));
    }

    @GetMapping("/api/v1/admin/restaurants/{id}")
    @Operation(summary = "Get a restaurant's full admin-panel detail")
    public ApiResponse<RestaurantDetailResponse> getById(@PathVariable Long id) {
        return ApiResponse.success(restaurantService.getById(id));
    }

    @GetMapping("/api/v1/admin/restaurant-categories")
    @Operation(summary = "List every restaurant category, active or not")
    public ApiResponse<PageResponse<RestaurantCategoryResponse>> listCategories(
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success(restaurantCategoryService.listPaged(pageable));
    }
}
