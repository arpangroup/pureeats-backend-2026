package com.pureeats.catalog.controller;

import com.pureeats.catalog.dto.SettingHistoryResponse;
import com.pureeats.catalog.dto.SettingSectionDefinition;
import com.pureeats.catalog.dto.SettingsUpdateRequest;
import com.pureeats.catalog.service.AppConfigService;
import com.pureeats.catalog.service.ContentService;
import com.pureeats.catalog.service.SettingHistoryService;
import com.pureeats.catalog.service.SettingSchemaService;
import com.pureeats.domain.common.exception.BadRequestException;
import com.pureeats.domain.common.response.ApiResponse;
import com.pureeats.domain.common.response.PageResponse;
import com.pureeats.user.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Admin write side of the generic settings key/value store exposed read-only (and publicly) at
 * GET /api/v1/settings — ADMIN or SUPER_ADMIN only. See ContentService#updateSettings for why this
 * stays a generic map rather than a typed entity per setting, and SettingSchemaService for the
 * section/field registry the admin panel renders off and every write here is validated against.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@Tag(name = "Admin Settings", description = "Generic platform settings (key/value) - ADMIN or SUPER_ADMIN only")
public class AdminSettingsController {

    private final ContentService contentService;
    private final SettingSchemaService settingSchemaService;
    private final SettingHistoryService settingHistoryService;
    private final AppConfigService appConfigService;

    @GetMapping("/api/v1/admin/settings/schema")
    @Operation(summary = "Every settings section/group/field the admin panel should render, and their types/defaults/help copy")
    public ApiResponse<List<SettingSectionDefinition>> schema() {
        return ApiResponse.success(settingSchemaService.schema());
    }

    @PutMapping("/api/v1/admin/settings")
    @Operation(summary = "Upsert one or more settings by key - rejects any key not in the schema, creates the row if it doesn't already exist otherwise")
    public ApiResponse<Map<String, String>> update(@AuthenticationPrincipal AuthenticatedUser principal, @RequestBody SettingsUpdateRequest request) {
        Map<String, String> updates = request.updates();
        log.info("Admin {} updating {} setting(s): {}", principal.userId(), updates.size(), updates.keySet());
        if (!appConfigService.verifyConfirmationPassword(request.confirmationPassword())) {
            throw new BadRequestException("Incorrect confirmation password");
        }
        return ApiResponse.success("Settings updated", contentService.updateSettings(updates, principal.userId()));
    }

    @GetMapping("/api/v1/admin/settings/history")
    @Operation(summary = "Field-level change history across both settings stores (generic key/value + AppConfig) - who changed what, from what, to what, when")
    public ApiResponse<PageResponse<SettingHistoryResponse>> history(
            @RequestParam(required = false) String fieldKey,
            @RequestParam(required = false) String source,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success(settingHistoryService.list(fieldKey, source, pageable));
    }
}
