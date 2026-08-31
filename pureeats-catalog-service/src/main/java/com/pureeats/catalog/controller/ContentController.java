package com.pureeats.catalog.controller;

import com.pureeats.catalog.dto.LanguageResponse;
import com.pureeats.catalog.dto.PageResponse;
import com.pureeats.catalog.dto.PaymentGatewayResponse;
import com.pureeats.catalog.dto.PromoSliderResponse;
import com.pureeats.catalog.service.ContentService;
import com.pureeats.domain.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Content", description = "CMS pages, app settings, promo sliders, languages and payment gateways")
public class ContentController {

    private final ContentService contentService;

    @GetMapping("/api/v1/pages")
    @Operation(summary = "List CMS pages")
    public ApiResponse<List<PageResponse>> pages() {
        log.debug("Listing CMS pages");
        return ApiResponse.success(contentService.listPages());
    }

    @GetMapping("/api/v1/pages/{slug}")
    @Operation(summary = "Get a single CMS page by slug")
    public ApiResponse<PageResponse> page(@PathVariable String slug) {
        log.debug("Fetching CMS page '{}'", slug);
        return ApiResponse.success(contentService.getPage(slug));
    }

    @GetMapping("/api/v1/settings")
    @Operation(summary = "Get the public app settings blob")
    public ApiResponse<Map<String, String>> settings() {
        return ApiResponse.success(contentService.getPublicSettings());
    }

    @GetMapping("/api/v1/promo-sliders")
    @Operation(summary = "List active promo sliders with their slides")
    public ApiResponse<List<PromoSliderResponse>> promoSliders() {
        return ApiResponse.success(contentService.listPromoSliders());
    }

    @GetMapping("/api/v1/languages")
    @Operation(summary = "List available languages")
    public ApiResponse<List<LanguageResponse>> languages() {
        return ApiResponse.success(contentService.listLanguages());
    }

    @GetMapping("/api/v1/payment-gateways")
    @Operation(summary = "List active payment gateways")
    public ApiResponse<List<PaymentGatewayResponse>> paymentGateways() {
        return ApiResponse.success(contentService.listPaymentGateways());
    }
}
