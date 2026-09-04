package com.pureeats.catalog.controller;

import com.pureeats.catalog.dto.LocationResponse;
import com.pureeats.catalog.dto.PopularGeoPlaceResponse;
import com.pureeats.catalog.service.LocationService;
import com.pureeats.domain.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/locations")
@RequiredArgsConstructor
@Tag(name = "Locations", description = "Serviceable locations and popular geo places")
public class LocationController {

    private final LocationService locationService;

    @GetMapping
    @Operation(summary = "List every active serviceable location")
    public ApiResponse<List<LocationResponse>> active() {
        return ApiResponse.success(locationService.active());
    }

    @GetMapping("/search")
    @Operation(summary = "Search locations by name")
    public ApiResponse<List<LocationResponse>> search(@RequestParam("q") String query) {
        log.debug("Searching locations for '{}'", query);
        return ApiResponse.success(locationService.search(query));
    }

    @GetMapping("/popular")
    @Operation(summary = "List popular locations")
    public ApiResponse<List<LocationResponse>> popular() {
        return ApiResponse.success(locationService.popular());
    }

    @GetMapping("/popular-geo-places")
    @Operation(summary = "List popular geo places (lat/long pins)")
    public ApiResponse<List<PopularGeoPlaceResponse>> popularGeoPlaces() {
        return ApiResponse.success(locationService.popularGeoPlaces());
    }
}
