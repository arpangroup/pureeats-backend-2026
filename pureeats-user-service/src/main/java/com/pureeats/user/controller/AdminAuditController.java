package com.pureeats.user.controller;

import com.pureeats.domain.common.response.ApiResponse;
import com.pureeats.domain.common.response.PageResponse;
import com.pureeats.user.dto.AuditLogResponse;
import com.pureeats.user.dto.LoginHistoryResponse;
import com.pureeats.user.dto.OtpChallengeResponse;
import com.pureeats.user.dto.RateLimitBucketResponse;
import com.pureeats.user.dto.SecurityBlockEntryResponse;
import com.pureeats.user.dto.UserDeviceResponse;
import com.pureeats.user.dto.UserSessionResponse;
import com.pureeats.user.enums.BlockType;
import com.pureeats.user.service.AdminAuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/**
 * Read-only security/audit observability for admins - the seven tables backing the OTP-challenge
 * auth flow's blocklist, rate limiting, sessions, devices, login history and audit log.
 * <p>
 * Authorization is deliberately layered two ways: {@code SecurityConfig}'s existing
 * {@code "/api/v1/admin/**" -> hasAnyRole("ADMIN", "SUPER_ADMIN")} URL rule already covers every
 * endpoint here, and the class-level {@link PreAuthorize} below enforces the same rule again at
 * the method-interception layer (requires {@code @EnableMethodSecurity}, enabled in
 * {@code pureeats-app}'s {@code SecurityConfig}). Neither layer depends on the other - removing
 * one still leaves the endpoints protected by the other.
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@Tag(name = "Admin Audit", description = "Read-only security/audit views - ADMIN or SUPER_ADMIN only")
public class AdminAuditController {

    private final AdminAuditService adminAuditService;

    @GetMapping("/audit-logs")
    @Operation(summary = "List security/activity audit events, optionally scoped to one user")
    public ApiResponse<PageResponse<AuditLogResponse>> listAuditLogs(
            @RequestParam(required = false) Long userId,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success(adminAuditService.listAuditLogs(userId, pageable));
    }

    @GetMapping("/login-history")
    @Operation(summary = "List login attempts (success and failure), optionally scoped to one user")
    public ApiResponse<PageResponse<LoginHistoryResponse>> listLoginHistory(
            @RequestParam(required = false) Long userId,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success(adminAuditService.listLoginHistory(userId, pageable));
    }

    @GetMapping("/otp-challenges")
    @Operation(summary = "List OTP challenges (never the OTP itself), optionally scoped to one user")
    public ApiResponse<PageResponse<OtpChallengeResponse>> listOtpChallenges(
            @RequestParam(required = false) Long userId,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success(adminAuditService.listOtpChallenges(userId, pageable));
    }

    @GetMapping("/rate-limit-buckets")
    @Operation(summary = "List rate-limit counter buckets")
    public ApiResponse<PageResponse<RateLimitBucketResponse>> listRateLimitBuckets(
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success(adminAuditService.listRateLimitBuckets(pageable));
    }

    @GetMapping("/security-blocklist")
    @Operation(summary = "List IP/device/email/phone/user blocklist entries, optionally filtered by type")
    public ApiResponse<PageResponse<SecurityBlockEntryResponse>> listSecurityBlockEntries(
            @RequestParam(required = false) BlockType blockType,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success(adminAuditService.listSecurityBlockEntries(blockType, pageable));
    }

    @GetMapping("/user-devices")
    @Operation(summary = "List known devices, optionally scoped to one user")
    public ApiResponse<PageResponse<UserDeviceResponse>> listUserDevices(
            @RequestParam(required = false) Long userId,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success(adminAuditService.listUserDevices(userId, pageable));
    }

    @GetMapping("/user-sessions")
    @Operation(summary = "List refresh-token sessions (live and revoked), optionally scoped to one user")
    public ApiResponse<PageResponse<UserSessionResponse>> listUserSessions(
            @RequestParam(required = false) Long userId,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success(adminAuditService.listUserSessions(userId, pageable));
    }
}
