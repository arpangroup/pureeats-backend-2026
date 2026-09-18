package com.pureeats.user.dto;

import com.pureeats.domain.enums.Role;

/**
 * Admin-panel "edit user" form (see UserDetailView.tsx's {@code handleSave}/{@code handleRoleChange}
 * — both call the same {@code PUT /api/v1/admin/users/{id}}, one with identity fields, the other
 * with just {@code role}). Every field is optional/nullable - only the ones the admin actually
 * changed are sent, and {@code null} means "leave as-is", not "clear this field". Audit fields
 * (updatedBy/updatedAt) are deliberately NOT part of this DTO even though the frontend sends them -
 * the server computes those itself from the authenticated principal and the current time, the same
 * way {@link AdminUserService#uploadPhoto} already does.
 */
public record AdminUserUpdateRequest(
        String name,
        String email,
        String phone,
        Boolean isActive,
        Role role
) {
}
