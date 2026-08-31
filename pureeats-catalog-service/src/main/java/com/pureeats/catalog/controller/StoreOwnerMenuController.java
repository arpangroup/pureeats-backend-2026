package com.pureeats.catalog.controller;

import com.pureeats.catalog.dto.*;
import com.pureeats.catalog.service.MenuService;
import com.pureeats.domain.common.response.ApiResponse;
import com.pureeats.user.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/store-owner")
@RequiredArgsConstructor
@Tag(name = "Store owner - Menu", description = "Item categories and menu items for a restaurant owner")
@SecurityRequirement(name = "bearerAuth")
public class StoreOwnerMenuController {

    private final MenuService menuService;

    @GetMapping("/item-categories")
    @Operation(summary = "List the signed-in owner's item categories")
    public ApiResponse<List<ItemCategoryResponse>> listCategories(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.success(menuService.listCategories(principal.userId()));
    }

    @PostMapping("/item-categories")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an item category")
    public ApiResponse<ItemCategoryResponse> createCategory(@AuthenticationPrincipal AuthenticatedUser principal,
                                                              @Valid @RequestBody ItemCategoryRequest request) {
        log.debug("Owner {}: create item category '{}'", principal.userId(), request.name());
        return ApiResponse.success("Category created", menuService.createCategory(principal.userId(), request));
    }

    @PatchMapping("/item-categories/{id}/enable")
    @Operation(summary = "Enable an item category")
    public ApiResponse<Void> enableCategory(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long id) {
        log.debug("Owner {}: enable item category {}", principal.userId(), id);
        menuService.setCategoryEnabled(principal.userId(), id, true);
        return ApiResponse.success("Category enabled", null);
    }

    @PatchMapping("/item-categories/{id}/disable")
    @Operation(summary = "Disable an item category")
    public ApiResponse<Void> disableCategory(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long id) {
        log.debug("Owner {}: disable item category {}", principal.userId(), id);
        menuService.setCategoryEnabled(principal.userId(), id, false);
        return ApiResponse.success("Category disabled", null);
    }

    @PostMapping("/restaurants/{restaurantId}/items")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a menu item to a restaurant you own")
    public ApiResponse<ItemResponse> createItem(@AuthenticationPrincipal AuthenticatedUser principal,
                                                 @PathVariable Long restaurantId,
                                                 @Valid @RequestBody ItemRequest request) {
        log.debug("Owner {}: create item '{}' for restaurant {}", principal.userId(), request.name(), restaurantId);
        return ApiResponse.success("Item created", menuService.createItem(principal.userId(), restaurantId, request));
    }

    @PutMapping("/items/{itemId}")
    @Operation(summary = "Update a menu item you own")
    public ApiResponse<ItemResponse> updateItem(@AuthenticationPrincipal AuthenticatedUser principal,
                                                 @PathVariable Long itemId,
                                                 @Valid @RequestBody ItemRequest request) {
        log.debug("Owner {}: update item {}", principal.userId(), itemId);
        return ApiResponse.success("Item updated", menuService.updateItem(principal.userId(), itemId, request));
    }

    @PatchMapping("/items/{itemId}/enable")
    @Operation(summary = "Enable a menu item")
    public ApiResponse<Void> enableItem(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long itemId) {
        log.debug("Owner {}: enable item {}", principal.userId(), itemId);
        menuService.setItemEnabled(principal.userId(), itemId, true);
        return ApiResponse.success("Item enabled", null);
    }

    @PatchMapping("/items/{itemId}/disable")
    @Operation(summary = "Disable a menu item")
    public ApiResponse<Void> disableItem(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long itemId) {
        log.debug("Owner {}: disable item {}", principal.userId(), itemId);
        menuService.setItemEnabled(principal.userId(), itemId, false);
        return ApiResponse.success("Item disabled", null);
    }
}
