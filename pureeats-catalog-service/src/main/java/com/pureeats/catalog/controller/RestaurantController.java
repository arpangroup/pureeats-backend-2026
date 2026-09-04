package com.pureeats.catalog.controller;

import com.pureeats.catalog.dto.*;
import com.pureeats.catalog.service.MenuService;
import com.pureeats.catalog.service.RestaurantService;
import com.pureeats.domain.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/restaurants")
@RequiredArgsConstructor
@Tag(name = "Restaurants", description = "Public restaurant discovery and menu browsing")
public class RestaurantController {

    private final RestaurantService restaurantService;
    private final MenuService menuService;

    @GetMapping
    @Operation(summary = "List active, accepted restaurants")
    public ApiResponse<List<RestaurantSummaryResponse>> list() {
        return ApiResponse.success(restaurantService.listActive());
    }

    @GetMapping("/nearby")
    @Operation(summary = "Find active restaurants within delivery range of a lat/lng, nearest first",
            description = "Serves off the cached active-restaurant list (see RestaurantService#cachedActiveRestaurants) rather than a fresh DB query per call. "
                    + "Without radiusKm, each restaurant's own configured delivery radius is the cutoff; with it, that override applies to every restaurant instead.")
    public ApiResponse<List<RestaurantSummaryResponse>> nearby(@RequestParam String lat, @RequestParam String lng,
                                                                 @RequestParam(required = false) BigDecimal radiusKm) {
        log.debug("Finding restaurants near ({}, {}) radiusKm={}", lat, lng, radiusKm);
        return ApiResponse.success(restaurantService.findNearby(lat, lng, radiusKm));
    }

    @GetMapping("/search")
    @Operation(summary = "Search restaurants by name")
    public ApiResponse<List<RestaurantSummaryResponse>> search(@RequestParam("q") String query) {
        log.debug("Searching restaurants for '{}'", query);
        return ApiResponse.success(restaurantService.search(query));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get restaurant details by id")
    public ApiResponse<RestaurantDetailResponse> getById(@PathVariable Long id) {
        log.debug("Fetching restaurant {}", id);
        return ApiResponse.success(restaurantService.getById(id));
    }

    @GetMapping("/slug/{slug}")
    @Operation(summary = "Get restaurant details by slug")
    public ApiResponse<RestaurantDetailResponse> getBySlug(@PathVariable String slug) {
        log.debug("Fetching restaurant by slug '{}'", slug);
        return ApiResponse.success(restaurantService.getBySlug(slug));
    }

    @GetMapping("/{id}/items")
    @Operation(summary = "Get a restaurant's active menu items")
    public ApiResponse<List<ItemResponse>> menu(@PathVariable Long id) {
        log.debug("Fetching menu for restaurant {}", id);
        return ApiResponse.success(menuService.getMenu(id));
    }

    @PostMapping("/{id}/check-delivery-area")
    @Operation(summary = "Check whether a lat/long falls within this restaurant's delivery radius")
    public ApiResponse<DeliveryAreaCheckResponse> checkDeliveryArea(@PathVariable Long id,
                                                                     @Valid @RequestBody DeliveryAreaCheckRequest request) {
        log.debug("Checking delivery area for restaurant {}", id);
        return ApiResponse.success(restaurantService.checkDeliveryArea(id, request.latitude(), request.longitude()));
    }
}
