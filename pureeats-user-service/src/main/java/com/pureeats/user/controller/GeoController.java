package com.pureeats.user.controller;

import com.pureeats.domain.common.response.ApiResponse;
import com.pureeats.user.dto.IpLocationResponse;
import com.pureeats.user.dto.ReverseGeocodeResponse;
import com.pureeats.user.security.geolocation.GeoLocation;
import com.pureeats.user.security.geolocation.IpGeolocationService;
import com.pureeats.user.security.geolocation.ReverseGeocodeResult;
import com.pureeats.user.security.geolocation.ReverseGeocodingService;
import com.pureeats.user.security.metadata.RequestMetadataResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public, unauthenticated location hints for guests and for the home page's device-location flow -
 * a signed-out customer has no saved address to price delivery against, so this gives the
 * Cart/Checkout page (via IP) and the home page (via GPS coordinates) a starting point. Both
 * proxy a free third-party provider server-side (see {@link IpGeolocationService} and
 * {@link ReverseGeocodingService}) rather than having the browser call them directly - see
 * docs/location-resolution/README.md for why.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Geo", description = "Public IP-based and reverse-geocoding location hints")
public class GeoController {

    private final RequestMetadataResolver requestMetadataResolver;
    private final IpGeolocationService ipGeolocationService;
    private final ReverseGeocodingService reverseGeocodingService;

    @GetMapping("/api/v1/geo/ip-location")
    @Operation(summary = "Best-effort location for the caller's IP - null fields if it can't be resolved")
    public ApiResponse<IpLocationResponse> ipLocation(HttpServletRequest request) {
        String ip = requestMetadataResolver.resolve(request).ipAddress();
        GeoLocation location = ipGeolocationService.resolve(ip).orElse(null);
        if (location == null) {
            log.debug("Could not resolve geolocation for IP {}", ip);
            return ApiResponse.success(new IpLocationResponse(null, null, null, null));
        }
        return ApiResponse.success(new IpLocationResponse(location.latitude(), location.longitude(), location.city(), location.country()));
    }

    @GetMapping("/api/v1/geo/reverse-geocode")
    @Operation(summary = "Best-effort readable address for a lat/lon pair - null fields if it can't be resolved")
    public ApiResponse<ReverseGeocodeResponse> reverseGeocode(@RequestParam double lat, @RequestParam double lon) {
        if (lat < -90 || lat > 90 || lon < -180 || lon > 180) {
            return ApiResponse.success(new ReverseGeocodeResponse(null, null, null, null, null));
        }
        ReverseGeocodeResult result = reverseGeocodingService.resolve(lat, lon).orElse(null);
        if (result == null) {
            log.debug("Could not reverse geocode {},{}", lat, lon);
            return ApiResponse.success(new ReverseGeocodeResponse(null, null, null, null, null));
        }
        return ApiResponse.success(new ReverseGeocodeResponse(result.displayName(), result.city(), result.state(), result.country(), result.postcode()));
    }
}
