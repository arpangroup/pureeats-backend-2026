package com.pureeats.user.service;

import com.pureeats.domain.common.exception.ResourceNotFoundException;
import com.pureeats.domain.common.response.PageResponse;
import com.pureeats.domain.entity.User;
import com.pureeats.domain.enums.Role;
import com.pureeats.user.dto.AdminUserResponse;
import com.pureeats.user.repository.AdminUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Admin-panel user listing/detail - read-only, gated by {@code /api/v1/admin/**} at the URL layer. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminUserService {

    private static final String USER_MORPH_TYPE = "App\\User";

    private final AdminUserRepository adminUserRepository;
    private final RoleService roleService;

    public PageResponse<AdminUserResponse> listUsers(Role userType, String search, Pageable pageable) {
        Role role = userType != null ? userType : Role.CUSTOMER;
        Page<User> page = adminUserRepository.findByRoleName(role.legacyName(), USER_MORPH_TYPE, search, pageable);
        return PageResponse.of(page.getContent().stream().map(u -> toResponse(u, role)).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    public AdminUserResponse getUser(Long id) {
        User user = adminUserRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));
        return toResponse(user, roleService.resolveRole(user.getId()));
    }

    private AdminUserResponse toResponse(User u, Role role) {
        return new AdminUserResponse(u.getId(), u.getName(), u.getEmail(), u.getPhone(), u.getPhoto(), role,
                User.STATUS_ACTIVE.equals(u.getIsActive()), u.getDefaultAddressId(), u.getDeliveryGuyDetailId(),
                u.getDeliveryPin(), u.getCreatedAt(), u.getUpdatedAt());
    }
}
