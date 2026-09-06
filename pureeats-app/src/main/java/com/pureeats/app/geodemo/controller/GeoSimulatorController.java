package com.pureeats.app.geodemo.controller;

import com.pureeats.app.geodemo.dto.GeoSimulatorRestaurant;
import com.pureeats.app.geodemo.dto.NearbyRestaurantResult;
import com.pureeats.domain.common.response.ApiResponse;
import com.pureeats.geo.LatLng;
import com.pureeats.geo.distance.DistanceCalculator;
import com.pureeats.geo.distance.DistanceMatrixResult;
import com.pureeats.geo.routing.Route;
import com.pureeats.geo.routing.RoutingEngine;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Backs the standalone {@code geo-delivery-simulator} (repo root, outside the Maven build) with the
 * real {@link DistanceCalculator}/{@link RoutingEngine} beans instead of that page's client-side JS
 * port of the same algorithms - its "live backend" toggle calls these endpoints when this app is
 * running and reachable, and falls back to its own JS simulation otherwise. Dummy restaurant data
 * only, on purpose: this is a demo surface for {@code pureeats-geo-service}, not the real restaurant
 * search API (see {@code RestaurantController} for that).
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Geo simulator (demo)", description = "Dummy-data demo endpoints backing geo-delivery-simulator/index.html")
public class GeoSimulatorController {

    private static final List<GeoSimulatorRestaurant> DUMMY_RESTAURANTS = List.of(
            new GeoSimulatorRestaurant(1L, "Hyderabad Downtown Biryani House", "17.385", "78.486"),
            new GeoSimulatorRestaurant(2L, "HiTech City Grill", "17.446", "78.382"),
            new GeoSimulatorRestaurant(3L, "Jubilee Hills Cafe", "17.427", "78.410"),
            new GeoSimulatorRestaurant(4L, "Banjara Hills Bites", "17.412", "78.438"),
            new GeoSimulatorRestaurant(5L, "Gachibowli Express", "17.444", "78.348"),
            new GeoSimulatorRestaurant(6L, "Secunderabad Diner", "17.451", "78.499")
    );

    private final DistanceCalculator distanceCalculator;
    private final RoutingEngine routingEngine;

    @GetMapping("/api/v1/geo/simulator/restaurants")
    @Operation(summary = "The demo's fixed dummy restaurant list")
    public ApiResponse<List<GeoSimulatorRestaurant>> restaurants() {
        return ApiResponse.success(DUMMY_RESTAURANTS);
    }

    @GetMapping("/api/v1/geo/simulator/nearby")
    @Operation(summary = "DistanceCalculator.topNNearest + etaMinutes over the dummy restaurants")
    public ApiResponse<List<NearbyRestaurantResult>> nearby(@RequestParam String lat, @RequestParam String lng,
                                                             @RequestParam(defaultValue = "6") int limit) {
        List<NearbyRestaurantResult> results = distanceCalculator
                .topNNearest(lat, lng, DUMMY_RESTAURANTS, limit, r -> new LatLng(r.lat(), r.lng()))
                .stream()
                .map(r -> new NearbyRestaurantResult(r.item(), r.distanceKm(),
                        distanceCalculator.etaMinutes(lat, lng, r.item().lat(), r.item().lng())))
                .toList();
        return ApiResponse.success(results);
    }

    @GetMapping("/api/v1/geo/simulator/distance-matrix")
    @Operation(summary = "DistanceCalculator.distanceMatrix - one origin against every dummy restaurant")
    public ApiResponse<DistanceMatrixResult> distanceMatrix(@RequestParam String lat, @RequestParam String lng) {
        List<LatLng> origins = List.of(new LatLng(lat, lng));
        List<LatLng> destinations = DUMMY_RESTAURANTS.stream().map(r -> new LatLng(r.lat(), r.lng())).toList();
        return ApiResponse.success(distanceCalculator.distanceMatrix(origins, destinations));
    }

    @GetMapping("/api/v1/geo/simulator/route")
    @Operation(summary = "RoutingEngine.route between two arbitrary points")
    public ApiResponse<Route> route(@RequestParam String fromLat, @RequestParam String fromLng,
                                     @RequestParam String toLat, @RequestParam String toLng) {
        return ApiResponse.success(routingEngine.route(fromLat, fromLng, toLat, toLng));
    }
}
