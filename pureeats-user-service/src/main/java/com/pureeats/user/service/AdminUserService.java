package com.pureeats.user.service;

import com.pureeats.domain.common.exception.ResourceNotFoundException;
import com.pureeats.domain.common.response.PageResponse;
import com.pureeats.domain.entity.DeliveryGuyDetail;
import com.pureeats.domain.entity.User;
import com.pureeats.domain.enums.Role;
import com.pureeats.media.service.MediaAssetService;
import com.pureeats.media.storage.MediaUrlResolver;
import com.pureeats.user.dto.AdminUserResponse;
import com.pureeats.user.dto.AdminUserUpdateRequest;
import com.pureeats.user.repository.AdminUserRepository;
import com.pureeats.user.repository.DeliveryGuyDetailRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;

/** Admin-panel user directory - listing/detail is read-only, plus a scoped update path (identity fields, active flag, role grant) and a photo upload, gated by {@code /api/v1/admin/**} at the URL layer. */
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
    private final DeliveryGuyDetailRepository deliveryGuyDetailRepository;
    private final RiderService riderService;

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
        // Only the single-record detail fetch falls back to DeliveryGuyDetail.photo when
        // User.photo is blank (not listUsers, to avoid an N+1 lookup per row on a list) - this
        // is what fixes an EXISTING mismatch (a rider who already uploaded via the app, before
        // uploadPhoto started keeping both columns in sync) without a data backfill; new writes
        // on either side now keep both columns equal so this fallback should rarely matter going
        // forward.
        String photo = user.getPhoto();
        if ((photo == null || photo.isBlank()) && user.getDeliveryGuyDetailId() != null) {
            photo = deliveryGuyDetailRepository.findById(user.getDeliveryGuyDetailId().longValue())
                    .map(DeliveryGuyDetail::getPhoto).orElse(photo);
        }
        return toResponse(user, roleService.resolveRole(user.getId()), photo);
    }

    /** Also mirrors onto DeliveryGuyDetail.photo when this user is a rider - see RiderService#uploadPhoto's own doc comment for why these two columns must be kept in sync (User.photo and DeliveryGuyDetail.photo are independent for historical reasons, and this is the other direction of the same fix: an admin uploading a photo here shouldn't leave the rider app or order-tracking's rider card showing a stale one). */
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
        if (user.getDeliveryGuyDetailId() != null) {
            deliveryGuyDetailRepository.findById(user.getDeliveryGuyDetailId().longValue()).ifPresent(detail -> {
                detail.setPhoto(storageKey);
                detail.setUpdatedAt(LocalDateTime.now());
                detail.setUpdatedBy(uploadedBy);
                deliveryGuyDetailRepository.save(detail);
            });
        }
        riderService.evictProfileCache(id);
        log.info("Admin {} updated photo for user {}", uploadedBy, id);
        return toResponse(user, roleService.resolveRole(id));
    }

    /**
     * Partial update - only non-null fields on {@code request} are applied, so the same endpoint
     * serves both the "save profile" form (name/email/phone/isActive) and the separate "change
     * role" action (role only) that UserDetailView.tsx uses. A role change is additive via
     * {@link RoleService#assignRole} (grants the new role, doesn't revoke any existing one) -
     * {@link RoleService#resolveRole}'s priority ordering means the highest-privilege held role
     * still wins, matching how every other role grant in this codebase already works.
     */
    @Transactional
    public AdminUserResponse updateUser(Long id, AdminUserUpdateRequest request, Long updatedBy) {
        User user = adminUserRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Admin {} attempted to update non-existent user {}", updatedBy, id);
                    return new ResourceNotFoundException("User not found.");
                });
        if (request.name() != null) user.setName(request.name());
        if (request.email() != null) user.setEmail(request.email());
        if (request.phone() != null) user.setPhone(request.phone());
        if (request.isActive() != null) user.setIsActive(request.isActive() ? User.STATUS_ACTIVE : User.STATUS_INACTIVE);
        user.setUpdatedAt(LocalDateTime.now());
        adminUserRepository.save(user);
        if (request.role() != null) {
            roleService.assignRole(id, request.role());
        }
        riderService.evictProfileCache(id);
        log.info("Admin {} updated user {}", updatedBy, id);
        return toResponse(user, roleService.resolveRole(id));
    }

    private AdminUserResponse toResponse(User u, Role role) {
        return toResponse(u, role, u.getPhoto());
    }

    private AdminUserResponse toResponse(User u, Role role, String photo) {
        return new AdminUserResponse(u.getId(), u.getName(), u.getEmail(), u.getPhone(), mediaUrlResolver.resolve(photo), role,
                User.STATUS_ACTIVE.equals(u.getIsActive()), u.getDefaultAddressId(), u.getDeliveryGuyDetailId(),
                u.getDeliveryPin(), u.getCreatedAt(), u.getUpdatedAt());
    }
}
