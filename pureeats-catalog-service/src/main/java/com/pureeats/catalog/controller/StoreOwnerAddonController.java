package com.pureeats.catalog.controller;

import com.pureeats.catalog.dto.AddonCategoryRequest;
import com.pureeats.catalog.dto.AddonCategoryResponse;
import com.pureeats.catalog.dto.AddonRequest;
import com.pureeats.catalog.dto.AddonResponse;
import com.pureeats.catalog.service.AddonService;
import com.pureeats.domain.common.response.ApiResponse;
import com.pureeats.user.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/store-owner")
@RequiredArgsConstructor
@Tag(name = "Store owner - Addons", description = "Addon categories and addons (e.g. size, extra toppings)")
@SecurityRequirement(name = "bearerAuth")
public class StoreOwnerAddonController {

    private final AddonService addonService;

    @GetMapping("/addon-categories")
    @Operation(summary = "List the signed-in owner's addon categories")
    public ApiResponse<List<AddonCategoryResponse>> listCategories(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.success(addonService.listCategories(principal.userId()));
    }

    @PostMapping("/addon-categories")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an addon category")
    public ApiResponse<AddonCategoryResponse> createCategory(@AuthenticationPrincipal AuthenticatedUser principal,
                                                               @Valid @RequestBody AddonCategoryRequest request) {
        return ApiResponse.success("Addon category created", addonService.createCategory(principal.userId(), request));
    }

    @GetMapping("/addon-categories/{addonCategoryId}/addons")
    @Operation(summary = "List addons in a category")
    public ApiResponse<List<AddonResponse>> listAddons(@PathVariable Long addonCategoryId) {
        return ApiResponse.success(addonService.listAddons(addonCategoryId));
    }

    @PostMapping("/addons")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an addon")
    public ApiResponse<AddonResponse> createAddon(@AuthenticationPrincipal AuthenticatedUser principal,
                                                   @Valid @RequestBody AddonRequest request) {
        return ApiResponse.success("Addon created", addonService.createAddon(principal.userId(), request));
    }

    @PatchMapping("/addons/{addonId}/enable")
    @Operation(summary = "Enable an addon")
    public ApiResponse<Void> enable(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long addonId) {
        addonService.setAddonEnabled(principal.userId(), addonId, true);
        return ApiResponse.success("Addon enabled", null);
    }

    @PatchMapping("/addons/{addonId}/disable")
    @Operation(summary = "Disable an addon")
    public ApiResponse<Void> disable(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long addonId) {
        addonService.setAddonEnabled(principal.userId(), addonId, false);
        return ApiResponse.success("Addon disabled", null);
    }
}
