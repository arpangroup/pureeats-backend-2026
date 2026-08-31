package com.pureeats.user.service;

import com.pureeats.domain.common.exception.ResourceNotFoundException;
import com.pureeats.domain.common.response.PageResponse;
import com.pureeats.domain.entity.User;
import com.pureeats.domain.enums.Role;
import com.pureeats.media.service.MediaAssetService;
import com.pureeats.media.storage.MediaUrlResolver;
import com.pureeats.user.dto.AdminUserResponse;
import com.pureeats.user.repository.AdminUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;

/** Admin-panel user listing/detail - read-only, gated by {@code /api/v1/admin/**} at the URL layer. */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminUserService {

    private static final String USER_MORPH_TYPE = "App\\User";

    private static final String OWNER_TYPE_USER = "USER";

    private final AdminUserRepository adminUserRepository;
    private final RoleService roleService;
    private final MediaUrlResolver mediaUrlResolver;
    private final MediaAssetService mediaAssetService;

    public PageResponse<AdminUserResponse> listUsers(Role userType, String search, Pageable pageable) {
        Role role = userType != null ? userType : Role.CUSTOMER;
        Page<User> page = adminUserRepository.findByRoleName(role.legacyName(), USER_MORPH_TYPE, search, pageable);
        log.debug("Admin listed {} users of role {}", page.getNumberOfElements(), role);
        return PageResponse.of(page.getContent().stream().map(u -> toResponse(u, role)).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    public AdminUserResponse getUser(Long id) {
        User user = adminUserRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Admin lookup for user {} found nothing", id);
                    return new ResourceNotFoundException("User not found.");
                });
        return toResponse(user, roleService.resolveRole(user.getId()));
    }

    @Transactional
    public AdminUserResponse uploadPhoto(Long id, MultipartFile file, Long uploadedBy) {
        User user = adminUserRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Admin {} attempted photo upload for non-existent user {}", uploadedBy, id);
                    return new ResourceNotFoundException("User not found.");
                });
        String storageKey = mediaAssetService.upload(file, OWNER_TYPE_USER, id, uploadedBy).storageKey();
        user.setPhoto(storageKey);
        user.setUpdatedAt(LocalDateTime.now());
        adminUserRepository.save(user);
        log.info("Admin {} updated photo for user {}", uploadedBy, id);
        return toResponse(user, roleService.resolveRole(id));
    }

    private AdminUserResponse toResponse(User u, Role role) {
        return new AdminUserResponse(u.getId(), u.getName(), u.getEmail(), u.getPhone(), mediaUrlResolver.resolve(u.getPhoto()), role,
                User.STATUS_ACTIVE.equals(u.getIsActive()), u.getDefaultAddressId(), u.getDeliveryGuyDetailId(),
                u.getDeliveryPin(), u.getCreatedAt(), u.getUpdatedAt());
    }
}
