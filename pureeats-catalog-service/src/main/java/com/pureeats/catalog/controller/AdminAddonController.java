package com.pureeats.catalog.controller;

import com.pureeats.catalog.dto.AddonCategoryRequest;
import com.pureeats.catalog.dto.AddonCategoryResponse;
import com.pureeats.catalog.dto.AddonRequest;
import com.pureeats.catalog.dto.AddonResponse;
import com.pureeats.catalog.service.AddonService;
import com.pureeats.domain.common.response.ApiResponse;
import com.pureeats.domain.common.response.PageResponse;
import com.pureeats.user.security.AuthenticatedUser;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Admin-panel addon directory - across every owner, not just one. */
@Slf4j
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@Tag(name = "Admin Addons", description = "Addon directory - ADMIN or SUPER_ADMIN only")
public class AdminAddonController {

    private final AddonService addonService;

    @GetMapping("/api/v1/admin/addon-categories")
    @Operation(summary = "List every addon category")
    public ApiResponse<PageResponse<AddonCategoryResponse>> listCategories(
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        log.debug("Admin: list addon categories, page {}", pageable.getPageNumber());
        return ApiResponse.success(addonService.listCategoriesPaged(pageable));
    }

    @PostMapping("/api/v1/admin/addon-categories")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an addon category")
    public ApiResponse<AddonCategoryResponse> createCategory(@AuthenticationPrincipal AuthenticatedUser principal,
                                                               @Valid @RequestBody AddonCategoryRequest request) {
        log.debug("Admin {}: create addon category '{}'", principal.userId(), request.name());
        return ApiResponse.success("Category created", addonService.createCategory(principal.userId(), request));
    }

    @PutMapping("/api/v1/admin/addon-categories/{id}")
    @Operation(summary = "Update an addon category")
    public ApiResponse<AddonCategoryResponse> updateCategory(@PathVariable Long id, @Valid @RequestBody AddonCategoryRequest request) {
        log.debug("Admin: update addon category {}", id);
        return ApiResponse.success("Category updated", addonService.updateCategoryAsAdmin(id, request));
    }

    @DeleteMapping("/api/v1/admin/addon-categories/{id}")
    @Operation(summary = "Delete an addon category")
    public ApiResponse<Void> deleteCategory(@PathVariable Long id) {
        log.debug("Admin: delete addon category {}", id);
        addonService.deleteCategoryAsAdmin(id);
        return ApiResponse.success("Category deleted", null);
    }

    @GetMapping("/api/v1/admin/addons")
    @Operation(summary = "List every addon, optionally scoped to one category")
    public ApiResponse<PageResponse<AddonResponse>> listAddons(
            @RequestParam(required = false) Long addonCategoryId,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        log.debug("Admin: list addons, category {} page {}", addonCategoryId, pageable.getPageNumber());
        return ApiResponse.success(addonService.listAddonsPaged(addonCategoryId, pageable));
    }

    @PostMapping("/api/v1/admin/addons")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an addon")
    public ApiResponse<AddonResponse> createAddon(@AuthenticationPrincipal AuthenticatedUser principal,
                                                   @Valid @RequestBody AddonRequest request) {
        log.debug("Admin {}: create addon '{}'", principal.userId(), request.name());
        return ApiResponse.success("Addon created", addonService.createAddon(principal.userId(), request));
    }

    @PutMapping("/api/v1/admin/addons/{id}")
    @Operation(summary = "Update an addon")
    public ApiResponse<AddonResponse> updateAddon(@PathVariable Long id, @Valid @RequestBody AddonRequest request) {
        log.debug("Admin: update addon {}", id);
        return ApiResponse.success("Addon updated", addonService.updateAddonAsAdmin(id, request));
    }

    @DeleteMapping("/api/v1/admin/addons/{id}")
    @Operation(summary = "Delete an addon")
    public ApiResponse<Void> deleteAddon(@PathVariable Long id) {
        log.debug("Admin: delete addon {}", id);
        addonService.deleteAddonAsAdmin(id);
        return ApiResponse.success("Addon deleted", null);
    }
}
