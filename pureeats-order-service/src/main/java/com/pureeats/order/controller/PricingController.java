package com.pureeats.order.controller;

import com.pureeats.catalog.repository.RestaurantRepository;
import com.pureeats.domain.common.exception.ResourceNotFoundException;
import com.pureeats.domain.common.response.ApiResponse;
import com.pureeats.domain.entity.Restaurant;
import com.pureeats.order.dto.DeliveryChargeResult;
import com.pureeats.order.dto.DeliveryQuoteResponse;
import com.pureeats.order.service.OrderPricingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public delivery-charge estimate for guests, who have no saved address (or account at all) to
 * validate a cart against - just the same Haversine-based math {@code OrderPricingService} already
 * uses for signed-in checkout, applied to whatever lat/lng the client has (typically the
 * IP-resolved location from {@code GeoController}, or the browser's own geolocation).
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Pricing", description = "Public delivery-charge estimate for guests")
public class PricingController {

    private final RestaurantRepository restaurantRepository;
    private final OrderPricingService orderPricingService;

    @GetMapping("/api/v1/pricing/delivery-quote")
    @Operation(summary = "Estimate delivery charge/distance from a restaurant to a lat/lng, no account needed")
    public ApiResponse<DeliveryQuoteResponse> deliveryQuote(@RequestParam Long restaurantId,
                                                             @RequestParam String lat, @RequestParam String lng) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found: " + restaurantId));
        DeliveryChargeResult result = orderPricingService.computeDeliveryCharge(restaurant, false, false, lat, lng);
        return ApiResponse.success(new DeliveryQuoteResponse(result.amount(), result.distanceKm(), result.basis()));
    }
}
