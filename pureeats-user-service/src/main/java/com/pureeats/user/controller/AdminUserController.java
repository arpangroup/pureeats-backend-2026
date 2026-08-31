package com.pureeats.user.controller;

import com.pureeats.domain.common.response.ApiResponse;
import com.pureeats.domain.common.response.PageResponse;
import com.pureeats.domain.enums.Role;
import com.pureeats.user.dto.AdminUserResponse;
import com.pureeats.user.security.AuthenticatedUser;
import com.pureeats.user.service.AdminUserService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Admin-panel user directory - list by {@code userType} (defaults to CUSTOMER) and detail. */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@Tag(name = "Admin Users", description = "Read-only user directory - ADMIN or SUPER_ADMIN only")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    @Operation(summary = "List users, optionally filtered by userType (defaults to CUSTOMER) and search")
    public ApiResponse<PageResponse<AdminUserResponse>> listUsers(
            @RequestParam(required = false) Role userType,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        log.debug("Admin listing users, userType={}", userType);
        return ApiResponse.success(adminUserService.listUsers(userType, search, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a user's full admin-panel detail")
    public ApiResponse<AdminUserResponse> getUser(@PathVariable Long id) {
        log.debug("Admin fetching user detail for {}", id);
        return ApiResponse.success(adminUserService.getUser(id));
    }

    @PostMapping("/{id}/photo")
    @Operation(summary = "Upload/replace a user's photo, as an admin")
    public ApiResponse<AdminUserResponse> uploadPhoto(@PathVariable Long id, @RequestParam("file") MultipartFile file,
                                                        @AuthenticationPrincipal AuthenticatedUser principal) {
        log.info("Admin {} uploading photo for user {}", principal.userId(), id);
        return ApiResponse.success("Photo updated", adminUserService.uploadPhoto(id, file, principal.userId()));
    }
}
