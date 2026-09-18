package com.pureeats.user.controller;

import com.pureeats.domain.common.response.ApiResponse;
import com.pureeats.domain.common.response.PageResponse;
import com.pureeats.domain.enums.Role;
import com.pureeats.user.dto.AddressResponse;
import com.pureeats.user.dto.AdminUserResponse;
import com.pureeats.user.dto.AdminUserUpdateRequest;
import com.pureeats.user.security.AuthenticatedUser;
import com.pureeats.user.service.AddressService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** Admin-panel user directory - list/detail by {@code userType} (defaults to CUSTOMER), a scoped update, and a photo upload. */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@Tag(name = "Admin Users", description = "User directory and edit - ADMIN or SUPER_ADMIN only")
public class AdminUserController {

    private final AdminUserService adminUserService;
    private final AddressService addressService;

    @GetMapping
    @Operation(summary = "List users, optionally filtered by userType (defaults to CUSTOMER), search, and accountStatus " +
            "(a status name, or \"ALL\" - defaults to ACTIVE-only, so deleted/blocked/disabled/locked accounts are filtered out unless asked for)")
    public ApiResponse<PageResponse<AdminUserResponse>> listUsers(
            @RequestParam(required = false) Role userType,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String accountStatus,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        log.debug("Admin listing users, userType={}, accountStatus={}", userType, accountStatus);
        return ApiResponse.success(adminUserService.listUsers(userType, search, accountStatus, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a user's full admin-panel detail")
    public ApiResponse<AdminUserResponse> getUser(@PathVariable Long id) {
        log.debug("Admin fetching user detail for {}", id);
        return ApiResponse.success(adminUserService.getUser(id));
    }

    @GetMapping("/{id}/addresses")
    @Operation(summary = "List a user's saved addresses, as an admin - includes which one is their active/default address")
    public ApiResponse<List<AddressResponse>> listAddresses(@PathVariable Long id) {
        log.debug("Admin listing addresses for user {}", id);
        return ApiResponse.success(addressService.list(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a user's identity fields (name/email/phone/isActive) and/or grant a role")
    public ApiResponse<AdminUserResponse> updateUser(@PathVariable Long id, @RequestBody AdminUserUpdateRequest request,
                                                       @AuthenticationPrincipal AuthenticatedUser principal) {
        log.info("Admin {} updating user {}", principal.userId(), id);
        return ApiResponse.success("User updated", adminUserService.updateUser(id, request, principal.userId()));
    }

    @PostMapping("/{id}/photo")
    @Operation(summary = "Upload/replace a user's photo, as an admin")
    public ApiResponse<AdminUserResponse> uploadPhoto(@PathVariable Long id, @RequestParam("file") MultipartFile file,
                                                        @AuthenticationPrincipal AuthenticatedUser principal) {
        log.info("Admin {} uploading photo for user {}", principal.userId(), id);
        return ApiResponse.success("Photo updated", adminUserService.uploadPhoto(id, file, principal.userId()));
    }
}
