package com.pureeats.user.dto;

import com.pureeats.domain.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Admin-panel "Add user" form (UsersListView.tsx's {@code handleSave}) - backs
 * {@code POST /api/v1/admin/users}. Powers Users/Employees/Restaurant Owners/Delivery Partners
 * creation alike, since they're all just a {@code User} row plus a role grant.
 */
public record AdminUserCreateRequest(
        @NotBlank String name,
        @NotBlank @Email String email,
        String phone,
        @NotNull Role role,
        Boolean isActive
) {
}
