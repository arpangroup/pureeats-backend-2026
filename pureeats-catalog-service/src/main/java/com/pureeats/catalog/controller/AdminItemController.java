package com.pureeats.catalog.controller;

import com.pureeats.catalog.dto.AdminItemCreateRequest;
import com.pureeats.catalog.dto.ItemBulkRequest;
import com.pureeats.catalog.dto.ItemBulkUploadResponse;
import com.pureeats.catalog.dto.ItemCategoryRequest;
import com.pureeats.catalog.dto.ItemCategoryResponse;
import com.pureeats.catalog.dto.ItemImageResponse;
import com.pureeats.catalog.dto.ItemPatchRequest;
import com.pureeats.catalog.dto.ItemResponse;
import com.pureeats.catalog.service.MenuService;
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
import org.springframework.web.multipart.MultipartFile;

/** Admin-panel menu-item directory - across every restaurant, not just one owner's. */
@Slf4j
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@Tag(name = "Admin Items", description = "Menu-item directory - ADMIN or SUPER_ADMIN only")
public class AdminItemController {

    private final MenuService menuService;

    @GetMapping("/api/v1/admin/items")
    @Operation(summary = "List every item, optionally scoped to one restaurant and/or a name search")
    public ApiResponse<PageResponse<ItemResponse>> list(
            @RequestParam(required = false) Long restaurantId,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        log.debug("Admin: list items, restaurant {} search '{}' page {}", restaurantId, search, pageable.getPageNumber());
        return ApiResponse.success(menuService.listItemsPaged(restaurantId, search, pageable));
    }

    @GetMapping("/api/v1/admin/items/{id}")
    @Operation(summary = "Get an item's detail")
    public ApiResponse<ItemResponse> getById(@PathVariable Long id) {
        log.debug("Admin: get item {}", id);
        return ApiResponse.success(menuService.getItem(id));
    }

    @PostMapping("/api/v1/admin/items")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an item for any restaurant")
    public ApiResponse<ItemResponse> create(@Valid @RequestBody AdminItemCreateRequest request) {
        log.debug("Admin: create item '{}' for restaurant {}", request.name(), request.restaurantId());
        return ApiResponse.success("Item created", menuService.createItemAsAdmin(request));
    }

    @PutMapping("/api/v1/admin/items/{id}")
    @Operation(summary = "Partially update an item - only non-null fields are applied")
    public ApiResponse<ItemResponse> update(@PathVariable Long id, @RequestBody ItemPatchRequest request) {
        log.debug("Admin: patch item {}", id);
        return ApiResponse.success("Item updated", menuService.patchItemAsAdmin(id, request));
    }

    @DeleteMapping("/api/v1/admin/items/{id}")
    @Operation(summary = "Delete an item")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        log.debug("Admin: delete item {}", id);
        menuService.deleteItemAsAdmin(id);
        return ApiResponse.success("Item deleted", null);
    }

    @PostMapping("/api/v1/admin/items/bulk")
    @Operation(summary = "Create many items at once, verifying each row's restaurant and category first")
    public ApiResponse<ItemBulkUploadResponse> bulkCreate(@Valid @RequestBody ItemBulkRequest request) {
        log.debug("Admin: bulk-create {} items", request.items().size());
        return ApiResponse.success(menuService.bulkCreateItems(request.items()));
    }

    @PostMapping("/api/v1/admin/items/{id}/image")
    @Operation(summary = "Upload/replace an item's image")
    public ApiResponse<ItemImageResponse> uploadImage(@AuthenticationPrincipal AuthenticatedUser principal,
                                                        @PathVariable Long id,
                                                        @RequestParam("file") MultipartFile file) {
        log.debug("Admin {}: upload image for item {}", principal.userId(), id);
        return ApiResponse.success("Image uploaded", menuService.uploadItemImage(id, file, principal.userId()));
    }

    @GetMapping("/api/v1/admin/item-categories")
    @Operation(summary = "List every item category, enabled or not")
    public ApiResponse<PageResponse<ItemCategoryResponse>> listCategories(
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        log.debug("Admin: list item categories, page {}", pageable.getPageNumber());
        return ApiResponse.success(menuService.listCategoriesPaged(pageable));
    }

    @PostMapping("/api/v1/admin/item-categories")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an item category")
    public ApiResponse<ItemCategoryResponse> createCategory(@AuthenticationPrincipal AuthenticatedUser principal,
                                                              @Valid @RequestBody ItemCategoryRequest request) {
        log.debug("Admin {}: create item category '{}'", principal.userId(), request.name());
        return ApiResponse.success("Category created", menuService.createCategory(principal.userId(), request));
    }

    @PutMapping("/api/v1/admin/item-categories/{id}")
    @Operation(summary = "Update an item category")
    public ApiResponse<ItemCategoryResponse> updateCategory(@PathVariable Long id, @Valid @RequestBody ItemCategoryRequest request) {
        log.debug("Admin: update item category {}", id);
        return ApiResponse.success("Category updated", menuService.updateCategoryAsAdmin(id, request));
    }

    @DeleteMapping("/api/v1/admin/item-categories/{id}")
    @Operation(summary = "Delete an item category")
    public ApiResponse<Void> deleteCategory(@PathVariable Long id) {
        log.debug("Admin: delete item category {}", id);
        menuService.deleteCategoryAsAdmin(id);
        return ApiResponse.success("Category deleted", null);
    }
}
