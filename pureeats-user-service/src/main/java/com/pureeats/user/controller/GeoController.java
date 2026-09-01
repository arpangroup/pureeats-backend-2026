package com.pureeats.user.controller;

import com.pureeats.domain.common.response.ApiResponse;
import com.pureeats.user.dto.IpLocationResponse;
import com.pureeats.user.security.geolocation.GeoLocation;
import com.pureeats.user.security.geolocation.IpGeolocationService;
import com.pureeats.user.security.metadata.RequestMetadataResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public, unauthenticated location hint for guests - a signed-out customer has no saved address to
 * price delivery against, so this gives the Cart/Checkout page a rough starting point (city-level
 * at best) via {@link IpGeolocationService}, which already existed for login-history auditing and
 * is just exposed here as well, not duplicated.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Geo", description = "Public IP-based location hint for guests")
public class GeoController {

    private final RequestMetadataResolver requestMetadataResolver;
    private final IpGeolocationService ipGeolocationService;

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
}
