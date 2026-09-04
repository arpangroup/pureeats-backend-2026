package com.pureeats.catalog.controller;

import com.pureeats.catalog.dto.LocationAdminResponse;
import com.pureeats.catalog.dto.LocationRequest;
import com.pureeats.catalog.service.LocationService;
import com.pureeats.domain.common.response.ApiResponse;
import com.pureeats.domain.common.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Admin CRUD for serviceable locations (the named-area tag restaurants and customers browse by) - ADMIN or SUPER_ADMIN only. */
@Slf4j
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@Tag(name = "Admin Locations", description = "Serviceable locations - ADMIN or SUPER_ADMIN only")
public class AdminLocationController {

    private final LocationService locationService;

    @GetMapping("/api/v1/admin/locations")
    @Operation(summary = "List every serviceable location, active or not")
    public ApiResponse<PageResponse<LocationAdminResponse>> list(
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        log.debug("Admin: list serviceable locations, page {}", pageable.getPageNumber());
        return ApiResponse.success(locationService.listPagedForAdmin(pageable));
    }

    @PostMapping("/api/v1/admin/locations")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a serviceable location")
    public ApiResponse<LocationAdminResponse> create(@Valid @RequestBody LocationRequest request) {
        log.debug("Admin: create serviceable location '{}'", request.name());
        return ApiResponse.success("Location created", locationService.create(request));
    }

    @PutMapping("/api/v1/admin/locations/{id}")
    @Operation(summary = "Update a serviceable location")
    public ApiResponse<LocationAdminResponse> update(@PathVariable Long id, @Valid @RequestBody LocationRequest request) {
        log.debug("Admin: update serviceable location {}", id);
        return ApiResponse.success("Location updated", locationService.update(id, request));
    }

    @DeleteMapping("/api/v1/admin/locations/{id}")
    @Operation(summary = "Delete a serviceable location")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        log.debug("Admin: delete serviceable location {}", id);
        locationService.delete(id);
        return ApiResponse.success("Location deleted", null);
    }
}
