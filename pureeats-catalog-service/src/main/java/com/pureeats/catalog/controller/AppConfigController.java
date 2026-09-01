package com.pureeats.catalog.controller;

import com.pureeats.catalog.dto.AppConfigAdminRequest;
import com.pureeats.catalog.dto.AppConfigAdminResponse;
import com.pureeats.catalog.dto.AppConfigResponse;
import com.pureeats.catalog.service.AppConfigService;
import com.pureeats.domain.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "App Config", description = "Remote app version/feature config the client fetches at boot")
public class AppConfigController {

    private final AppConfigService appConfigService;

    @GetMapping("/api/v1/app-config")
    @Operation(summary = "Fetch computed update severity + feature config for a client version")
    public ApiResponse<AppConfigResponse> get(@RequestParam(required = false) String clientVersion) {
        return ApiResponse.success(appConfigService.forClient(clientVersion));
    }

    @GetMapping("/api/v1/admin/app-config")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Get the raw stored app config for editing")
    public ApiResponse<AppConfigAdminResponse> getForAdmin() {
        return ApiResponse.success(appConfigService.getForAdmin());
    }

    @PutMapping("/api/v1/admin/app-config")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Update the app config - version thresholds, Google Maps key, enabled payment methods")
    public ApiResponse<AppConfigAdminResponse> update(@Valid @RequestBody AppConfigAdminRequest request) {
        log.info("Admin updating app config");
        return ApiResponse.success("App config updated", appConfigService.update(request));
    }
}
