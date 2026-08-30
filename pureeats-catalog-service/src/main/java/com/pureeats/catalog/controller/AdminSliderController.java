package com.pureeats.catalog.controller;

import com.pureeats.catalog.dto.AdminPromoSliderRequest;
import com.pureeats.catalog.dto.AdminPromoSliderResponse;
import com.pureeats.catalog.dto.AdminRestaurantCategorySliderRequest;
import com.pureeats.catalog.dto.AdminRestaurantCategorySliderResponse;
import com.pureeats.catalog.dto.AdminSlideRequest;
import com.pureeats.catalog.dto.AdminSlideResponse;
import com.pureeats.catalog.dto.SlideImageResponse;
import com.pureeats.catalog.service.AdminSliderService;
import com.pureeats.domain.common.response.ApiResponse;
import com.pureeats.domain.common.response.PageResponse;
import com.pureeats.user.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
import java.util.Map;

/** Admin CRUD for promo sliders, store-category sliders, and their slides - ADMIN or SUPER_ADMIN only. */
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@Tag(name = "Admin Sliders", description = "Slider and slide management - ADMIN or SUPER_ADMIN only")
public class AdminSliderController {

    private final AdminSliderService sliderService;

    // ---- Promo sliders ----

    @GetMapping("/api/v1/admin/promo-sliders")
    public ApiResponse<PageResponse<AdminPromoSliderResponse>> listPromoSliders(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success(sliderService.listPromoSliders(search, pageable));
    }

    @GetMapping("/api/v1/admin/promo-sliders/{id}")
    public ApiResponse<AdminPromoSliderResponse> getPromoSlider(@PathVariable Long id) {
        return ApiResponse.success(sliderService.getPromoSlider(id));
    }

    @PostMapping("/api/v1/admin/promo-sliders")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AdminPromoSliderResponse> createPromoSlider(@Valid @RequestBody AdminPromoSliderRequest request) {
        return ApiResponse.success("Slider created", sliderService.createPromoSlider(request));
    }

    @PutMapping("/api/v1/admin/promo-sliders/{id}")
    public ApiResponse<AdminPromoSliderResponse> updatePromoSlider(@PathVariable Long id, @Valid @RequestBody AdminPromoSliderRequest request) {
        return ApiResponse.success("Slider updated", sliderService.updatePromoSlider(id, request));
    }

    @DeleteMapping("/api/v1/admin/promo-sliders/{id}")
    public ApiResponse<Void> deletePromoSlider(@PathVariable Long id) {
        sliderService.deletePromoSlider(id);
        return ApiResponse.success("Slider deleted", null);
    }

    // ---- Restaurant category sliders ----

    @GetMapping("/api/v1/admin/restaurant-category-sliders")
    public ApiResponse<PageResponse<AdminRestaurantCategorySliderResponse>> listCategorySliders(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success(sliderService.listCategorySliders(search, pageable));
    }

    @GetMapping("/api/v1/admin/restaurant-category-sliders/{id}")
    public ApiResponse<AdminRestaurantCategorySliderResponse> getCategorySlider(@PathVariable Long id) {
        return ApiResponse.success(sliderService.getCategorySlider(id));
    }

    @PostMapping("/api/v1/admin/restaurant-category-sliders")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AdminRestaurantCategorySliderResponse> createCategorySlider(@Valid @RequestBody AdminRestaurantCategorySliderRequest request) {
        return ApiResponse.success("Slider created", sliderService.createCategorySlider(request));
    }

    @PutMapping("/api/v1/admin/restaurant-category-sliders/{id}")
    public ApiResponse<AdminRestaurantCategorySliderResponse> updateCategorySlider(@PathVariable Long id, @Valid @RequestBody AdminRestaurantCategorySliderRequest request) {
        return ApiResponse.success("Slider updated", sliderService.updateCategorySlider(id, request));
    }

    @DeleteMapping("/api/v1/admin/restaurant-category-sliders/{id}")
    public ApiResponse<Void> deleteCategorySlider(@PathVariable Long id) {
        sliderService.deleteCategorySlider(id);
        return ApiResponse.success("Slider deleted", null);
    }

    // ---- Slides ----

    @GetMapping("/api/v1/admin/slides")
    @Operation(summary = "List every slide belonging to one slider")
    public ApiResponse<List<AdminSlideResponse>> listSlides(
            @RequestParam String sliderType, @RequestParam Long sliderId) {
        return ApiResponse.success(sliderService.listSlides(sliderType, sliderId));
    }

    @GetMapping("/api/v1/admin/slides/counts")
    @Operation(summary = "Slide counts grouped by slider id, for one slider type")
    public ApiResponse<Map<Long, Long>> slideCounts(@RequestParam String sliderType) {
        return ApiResponse.success(sliderService.countsBySliderType(sliderType));
    }

    @PostMapping("/api/v1/admin/slides")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AdminSlideResponse> createSlide(@Valid @RequestBody AdminSlideRequest request) {
        return ApiResponse.success("Slide created", sliderService.createSlide(request));
    }

    @PutMapping("/api/v1/admin/slides/{id}")
    public ApiResponse<AdminSlideResponse> updateSlide(@PathVariable Long id, @Valid @RequestBody AdminSlideRequest request) {
        return ApiResponse.success("Slide updated", sliderService.updateSlide(id, request));
    }

    @DeleteMapping("/api/v1/admin/slides/{id}")
    public ApiResponse<Void> deleteSlide(@PathVariable Long id) {
        sliderService.deleteSlide(id);
        return ApiResponse.success("Slide deleted", null);
    }

    @PostMapping("/api/v1/admin/slides/{id}/image")
    public ApiResponse<SlideImageResponse> uploadSlideImage(@AuthenticationPrincipal AuthenticatedUser principal,
                                                              @PathVariable Long id,
                                                              @RequestParam("file") MultipartFile file) {
        return ApiResponse.success("Image uploaded", sliderService.uploadSlideImage(id, file, principal.userId()));
    }
}
