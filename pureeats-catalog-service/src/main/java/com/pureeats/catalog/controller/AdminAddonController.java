package com.pureeats.catalog.controller;

import com.pureeats.catalog.dto.AddonCategoryResponse;
import com.pureeats.catalog.dto.AddonResponse;
import com.pureeats.catalog.service.AddonService;
import com.pureeats.domain.common.response.ApiResponse;
import com.pureeats.domain.common.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin-panel addon directory - across every owner, not just one. */
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@Tag(name = "Admin Addons", description = "Read-only addon directory - ADMIN or SUPER_ADMIN only")
public class AdminAddonController {

    private final AddonService addonService;

    @GetMapping("/api/v1/admin/addon-categories")
    @Operation(summary = "List every addon category")
    public ApiResponse<PageResponse<AddonCategoryResponse>> listCategories(
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success(addonService.listCategoriesPaged(pageable));
    }

    @GetMapping("/api/v1/admin/addons")
    @Operation(summary = "List every addon, optionally scoped to one category")
    public ApiResponse<PageResponse<AddonResponse>> listAddons(
            @RequestParam(required = false) Long addonCategoryId,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success(addonService.listAddonsPaged(addonCategoryId, pageable));
    }
}
