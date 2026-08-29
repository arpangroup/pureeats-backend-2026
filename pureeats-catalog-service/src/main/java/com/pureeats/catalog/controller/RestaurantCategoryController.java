package com.pureeats.catalog.controller;

import com.pureeats.catalog.dto.RestaurantCategoryResponse;
import com.pureeats.catalog.dto.RestaurantSummaryResponse;
import com.pureeats.catalog.service.RestaurantCategoryService;
import com.pureeats.domain.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/restaurant-categories")
@RequiredArgsConstructor
@Tag(name = "Restaurant categories", description = "Cuisine/category browsing (e.g. Pizza, Chinese)")
public class RestaurantCategoryController {

    private final RestaurantCategoryService restaurantCategoryService;

    @GetMapping
    @Operation(summary = "List active restaurant categories")
    public ApiResponse<List<RestaurantCategoryResponse>> list() {
        return ApiResponse.success(restaurantCategoryService.listActive());
    }

    @GetMapping("/{id}/restaurants")
    @Operation(summary = "List restaurants belonging to a category")
    public ApiResponse<List<RestaurantSummaryResponse>> restaurants(@PathVariable Long id) {
        return ApiResponse.success(restaurantCategoryService.restaurantsInCategory(id));
    }
}
