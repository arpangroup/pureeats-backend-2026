package com.pureeats.rating.controller;

import com.pureeats.domain.common.response.ApiResponse;
import com.pureeats.rating.dto.*;
import com.pureeats.rating.service.RatingService;
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
@RequestMapping("/api/v1/ratings")
@RequiredArgsConstructor
@Tag(name = "Ratings", description = "Order, restaurant and driver ratings")
public class RatingController {

    private final RatingService ratingService;

    @GetMapping("/ratable-orders")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "List the signed-in user's delivered orders that are still unrated")
    public ApiResponse<List<RatableOrderResponse>> ratableOrders(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.success(ratingService.ratableOrders(principal.userId()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Submit a rating for a restaurant or driver on a delivered order")
    public ApiResponse<RatingResponse> submit(@AuthenticationPrincipal AuthenticatedUser principal,
                                               @Valid @RequestBody SubmitRatingRequest request) {
        return ApiResponse.success("Rating submitted", ratingService.submit(principal.userId(), request));
    }

    @GetMapping("/restaurants/{restaurantId}")
    @Operation(summary = "List a restaurant's ratings")
    public ApiResponse<List<RatingResponse>> restaurantRatings(@PathVariable Long restaurantId) {
        return ApiResponse.success(ratingService.restaurantRatings(restaurantId));
    }

    @GetMapping("/restaurants/{restaurantId}/average")
    @Operation(summary = "Get a restaurant's average rating")
    public ApiResponse<AverageRatingResponse> restaurantAverage(@PathVariable Long restaurantId) {
        return ApiResponse.success(ratingService.restaurantAverage(restaurantId));
    }

    @GetMapping("/drivers/{deliveryGuyDetailId}")
    @Operation(summary = "List a driver's ratings")
    public ApiResponse<List<RatingResponse>> driverRatings(@PathVariable Long deliveryGuyDetailId) {
        return ApiResponse.success(ratingService.driverRatings(deliveryGuyDetailId));
    }

    @GetMapping("/drivers/{deliveryGuyDetailId}/average")
    @Operation(summary = "Get a driver's average rating")
    public ApiResponse<AverageRatingResponse> driverAverage(@PathVariable Long deliveryGuyDetailId) {
        return ApiResponse.success(ratingService.driverAverage(deliveryGuyDetailId));
    }
}
