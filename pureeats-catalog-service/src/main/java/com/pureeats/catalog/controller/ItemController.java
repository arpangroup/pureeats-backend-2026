package com.pureeats.catalog.controller;

import com.pureeats.catalog.dto.RecommendedItemResponse;
import com.pureeats.catalog.service.MenuService;
import com.pureeats.domain.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/items")
@RequiredArgsConstructor
@Tag(name = "Items", description = "Cross-restaurant item discovery")
public class ItemController {

    private final MenuService menuService;

    @GetMapping("/recommended")
    @Operation(summary = "Cross-restaurant recommended items, for the Home page's Recommended section")
    public ApiResponse<List<RecommendedItemResponse>> recommended(@RequestParam(required = false, defaultValue = "12") Integer limit) {
        log.debug("Listing {} recommended items", limit);
        return ApiResponse.success(menuService.listRecommendedItems(limit));
    }

    @GetMapping("/search")
    @Operation(summary = "Cross-restaurant dish name search, for the Search page's Dishes tab")
    public ApiResponse<List<RecommendedItemResponse>> search(@RequestParam("q") String query, @RequestParam(required = false, defaultValue = "20") Integer limit) {
        log.debug("Searching items for '{}'", query);
        return ApiResponse.success(menuService.searchItems(query, limit));
    }
}
