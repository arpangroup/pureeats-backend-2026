package com.pureeats.catalog.controller;

import com.pureeats.catalog.dto.ItemCategoryResponse;
import com.pureeats.catalog.dto.ItemResponse;
import com.pureeats.catalog.service.MenuService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin-panel menu-item directory - across every restaurant, not just one owner's. */
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@Tag(name = "Admin Items", description = "Read-only menu-item directory - ADMIN or SUPER_ADMIN only")
public class AdminItemController {

    private final MenuService menuService;

    @GetMapping("/api/v1/admin/items")
    @Operation(summary = "List every item, optionally scoped to one restaurant and/or a name search")
    public ApiResponse<PageResponse<ItemResponse>> list(
            @RequestParam(required = false) Long restaurantId,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success(menuService.listItemsPaged(restaurantId, search, pageable));
    }

    @GetMapping("/api/v1/admin/items/{id}")
    @Operation(summary = "Get an item's detail")
    public ApiResponse<ItemResponse> getById(@PathVariable Long id) {
        return ApiResponse.success(menuService.getItem(id));
    }

    @GetMapping("/api/v1/admin/item-categories")
    @Operation(summary = "List every item category, enabled or not")
    public ApiResponse<PageResponse<ItemCategoryResponse>> listCategories(
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success(menuService.listCategoriesPaged(pageable));
    }
}
