package com.pureeats.catalog.controller;

import com.pureeats.catalog.dto.RestaurantAuditLogResponse;
import com.pureeats.catalog.dto.RestaurantCategoryRequest;
import com.pureeats.catalog.dto.RestaurantCategoryResponse;
import com.pureeats.catalog.dto.RestaurantCreateRequest;
import com.pureeats.catalog.dto.RestaurantDetailResponse;
import com.pureeats.catalog.dto.RestaurantImageResponse;
import com.pureeats.catalog.dto.RestaurantPatchRequest;
import com.pureeats.catalog.dto.RestaurantSummaryResponse;
import com.pureeats.catalog.service.RestaurantAuditLogService;
import com.pureeats.catalog.service.RestaurantCategoryService;
import com.pureeats.catalog.service.RestaurantService;
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

import java.util.List;

/** Admin-panel store directory - every restaurant regardless of active/accepted status. */
@Slf4j
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@Tag(name = "Admin Restaurants", description = "Store directory + updates - ADMIN or SUPER_ADMIN only")
public class AdminRestaurantController {

    private final RestaurantService restaurantService;
    private final RestaurantCategoryService restaurantCategoryService;
    private final RestaurantAuditLogService restaurantAuditLogService;

    @GetMapping("/api/v1/admin/restaurants")
    @Operation(summary = "List every restaurant, optionally filtered by a name search")
    public ApiResponse<PageResponse<RestaurantSummaryResponse>> list(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        log.debug("Admin: list restaurants, search '{}' page {}", search, pageable.getPageNumber());
        return ApiResponse.success(restaurantService.listPaged(search, pageable));
    }

    @GetMapping("/api/v1/admin/restaurants/{id}")
    @Operation(summary = "Get a restaurant's full admin-panel detail")
    public ApiResponse<RestaurantDetailResponse> getById(@PathVariable Long id) {
        log.debug("Admin: get restaurant {}", id);
        return ApiResponse.success(restaurantService.getById(id));
    }

    @PostMapping("/api/v1/admin/restaurants")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a restaurant as an admin - no owner-onboarding link, accepted immediately")
    public ApiResponse<RestaurantDetailResponse> create(@Valid @RequestBody RestaurantCreateRequest request) {
        log.debug("Admin: create restaurant '{}'", request.name());
        return ApiResponse.success("Restaurant created", restaurantService.createAsAdmin(request));
    }

    @PutMapping("/api/v1/admin/restaurants/{id}")
    @Operation(summary = "Partially update a restaurant - name/commissionRate/isActive/isAccepted/autoAcceptable/isFeatured are ADMIN/SUPER_ADMIN only")
    public ApiResponse<RestaurantDetailResponse> patch(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long id,
                                                         @RequestBody RestaurantPatchRequest request) {
        log.debug("Admin {}: patch restaurant {}", principal.userId(), id);
        return ApiResponse.success("Restaurant updated", restaurantService.patchAsAdmin(principal.userId(), id, request, principal.role()));
    }

    @DeleteMapping("/api/v1/admin/restaurants/{id}")
    @Operation(summary = "Delete a restaurant")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        log.debug("Admin: delete restaurant {}", id);
        restaurantService.deleteAsAdmin(id);
        return ApiResponse.success("Restaurant deleted", null);
    }

    @GetMapping("/api/v1/admin/restaurants/{id}/audit-log")
    @Operation(summary = "List field-level changes made to a restaurant - who changed what, from what, to what")
    public ApiResponse<PageResponse<RestaurantAuditLogResponse>> auditLog(
            @PathVariable Long id,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        log.debug("Admin: view audit log for restaurant {}", id);
        return ApiResponse.success(restaurantAuditLogService.journey(id, pageable));
    }

    @PostMapping("/api/v1/admin/restaurants/{id}/image")
    @Operation(summary = "Replace a restaurant's main/cover image (max 2MB)")
    public ApiResponse<RestaurantImageResponse> uploadCoverImage(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long id,
                                                                    @RequestParam("file") MultipartFile file) {
        log.debug("Admin {}: upload cover image for restaurant {}", principal.userId(), id);
        return ApiResponse.success("Image uploaded", restaurantService.uploadCoverImage(id, file, principal.userId()));
    }

    @PostMapping("/api/v1/admin/restaurants/{id}/images")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Upload an image to a restaurant's gallery (max 2MB, max 5 images per store)")
    public ApiResponse<RestaurantImageResponse> uploadImage(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long id,
                                                              @RequestParam("file") MultipartFile file) {
        log.debug("Admin {}: upload gallery image for restaurant {}", principal.userId(), id);
        return ApiResponse.success("Image uploaded", restaurantService.uploadImage(id, file, principal.userId()));
    }

    @GetMapping("/api/v1/admin/restaurants/{id}/images")
    @Operation(summary = "List a restaurant's gallery images")
    public ApiResponse<List<RestaurantImageResponse>> listImages(@PathVariable Long id) {
        log.debug("Admin: list gallery images for restaurant {}", id);
        return ApiResponse.success(restaurantService.listImages(id));
    }

    @DeleteMapping("/api/v1/admin/restaurants/{id}/images/{mediaId}")
    @Operation(summary = "Remove one image from a restaurant's gallery")
    public ApiResponse<Void> deleteImage(@PathVariable Long id, @PathVariable Long mediaId) {
        log.debug("Admin: delete gallery image {} from restaurant {}", mediaId, id);
        restaurantService.deleteImage(id, mediaId);
        return ApiResponse.success("Image removed", null);
    }

    @GetMapping("/api/v1/admin/restaurant-categories")
    @Operation(summary = "List every restaurant category, active or not")
    public ApiResponse<PageResponse<RestaurantCategoryResponse>> listCategories(
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        log.debug("Admin: list restaurant categories, page {}", pageable.getPageNumber());
        return ApiResponse.success(restaurantCategoryService.listPaged(pageable));
    }

    @PostMapping("/api/v1/admin/restaurant-categories")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a restaurant category")
    public ApiResponse<RestaurantCategoryResponse> createCategory(@Valid @RequestBody RestaurantCategoryRequest request) {
        log.debug("Admin: create restaurant category '{}'", request.name());
        return ApiResponse.success("Category created", restaurantCategoryService.create(request));
    }

    @PutMapping("/api/v1/admin/restaurant-categories/{id}")
    @Operation(summary = "Update a restaurant category")
    public ApiResponse<RestaurantCategoryResponse> updateCategory(@PathVariable Long id, @Valid @RequestBody RestaurantCategoryRequest request) {
        log.debug("Admin: update restaurant category {}", id);
        return ApiResponse.success("Category updated", restaurantCategoryService.update(id, request));
    }

    @DeleteMapping("/api/v1/admin/restaurant-categories/{id}")
    @Operation(summary = "Delete a restaurant category")
    public ApiResponse<Void> deleteCategory(@PathVariable Long id) {
        log.debug("Admin: delete restaurant category {}", id);
        restaurantCategoryService.delete(id);
        return ApiResponse.success("Category deleted", null);
    }
}
