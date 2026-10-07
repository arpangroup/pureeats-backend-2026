package com.pureeats.app.deliveryguy.service;

import com.pureeats.app.deliveryguy.dto.AdminDeliveryGuyRequest;
import com.pureeats.app.deliveryguy.dto.AdminDeliveryGuyResponse;
import com.pureeats.app.deliveryguy.dto.TripDetailResponse;
import com.pureeats.domain.common.PiiMaskUtil;
import com.pureeats.domain.common.exception.BadRequestException;
import com.pureeats.domain.common.exception.ResourceNotFoundException;
import com.pureeats.domain.common.response.PageResponse;
import com.pureeats.domain.entity.DeliveryGuyDetail;
import com.pureeats.domain.entity.DeliveryGuyRestaurant;
import com.pureeats.domain.entity.TripDetail;
import com.pureeats.domain.entity.User;
import com.pureeats.domain.enums.Role;
import com.pureeats.order.repository.TripDetailRepository;
import com.pureeats.user.repository.DeliveryGuyDetailRepository;
import com.pureeats.user.repository.DeliveryGuyRestaurantRepository;
import com.pureeats.user.repository.UserRepository;
import com.pureeats.user.service.RiderService;
import com.pureeats.user.service.RoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Admin CRUD for delivery partners. Lives in pureeats-app (like {@link com.pureeats.app.dashboard.service.DashboardService})
 * because it needs both pureeats-user-service (User/DeliveryGuyDetail/roles) and pureeats-order-service
 * (TripDetail, for earnings) - user-service can't depend on order-service (order-service already
 * depends on user-service, so the reverse would be circular), so this can't live in either.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminDeliveryGuyService {

    private final DeliveryGuyDetailRepository deliveryGuyDetailRepository;
    private final DeliveryGuyRestaurantRepository deliveryGuyRestaurantRepository;
    private final UserRepository userRepository;
    private final RoleService roleService;
    private final TripDetailRepository tripDetailRepository;
    private final RiderService riderService;
    private final com.pureeats.media.storage.MediaUrlResolver mediaUrlResolver;
    private final com.pureeats.user.service.RiderStatusLogService riderStatusLogService;
    private final com.pureeats.user.security.AccountAccessGuard accountAccessGuard;
    private final com.pureeats.order.service.OrderNotificationService orderNotificationService;
    private final com.pureeats.media.service.MediaAssetService mediaAssetService;

    @Transactional(readOnly = true)
    public PageResponse<AdminDeliveryGuyResponse> listPaged(String search, String approvalStatus, Pageable pageable) {
        Page<DeliveryGuyDetail> page = approvalStatus != null && !approvalStatus.isBlank()
                ? deliveryGuyDetailRepository.findPageByApproval(approvalStatus.trim().toUpperCase(), search, pageable)
                : deliveryGuyDetailRepository.findPage(search, pageable);
        return PageResponse.of(page.getContent().stream().map(this::toResponse).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    /** Admin uploads/replaces a partner's driving licence photo. */
    @Transactional
    public AdminDeliveryGuyResponse uploadLicensePhoto(Long id, org.springframework.web.multipart.MultipartFile file, Long adminUserId) {
        DeliveryGuyDetail detail = findOrThrow(id);
        mediaAssetService.upload(file, com.pureeats.user.service.RiderService.OWNER_TYPE_LICENSE, detail.getId(), adminUserId);
        findLinkedUser(id).ifPresent(user -> riderService.evictProfileCache(user.getId()));
        log.info("Admin {} uploaded a licence photo for delivery partner {}", adminUserId, id);
        return toResponse(detail);
    }

    /** Applications waiting for review - for the Approvals badge. */
    @Transactional(readOnly = true)
    public long pendingCount() {
        return deliveryGuyDetailRepository.countByApprovalStatus(DeliveryGuyDetail.APPROVAL_PENDING);
    }

    /** Approve or reject a partner's application; they're notified either way. Rejection needs a reason the partner can act on. */
    @Transactional
    public AdminDeliveryGuyResponse review(Long id, boolean approve, String reason, Long adminUserId) {
        DeliveryGuyDetail detail = findOrThrow(id);
        if (!approve && (reason == null || reason.isBlank())) {
            throw new BadRequestException("Give a reason so the partner knows what to fix.");
        }
        detail.setApprovalStatus(approve ? DeliveryGuyDetail.APPROVAL_APPROVED : DeliveryGuyDetail.APPROVAL_REJECTED);
        detail.setRejectionReason(approve ? null : reason.trim());
        detail.setApprovalUpdatedAt(LocalDateTime.now());
        detail.setApprovalUpdatedBy(adminUserId);
        if (!approve) detail.setIsOnline(false);
        detail.setUpdatedAt(LocalDateTime.now());
        deliveryGuyDetailRepository.save(detail);
        findLinkedUser(id).ifPresent(user -> {
            riderService.evictProfileCache(user.getId());
            orderNotificationService.notify(com.pureeats.notification.enums.NotificationRecipientRole.DELIVERY_PARTNER, user.getId(),
                    approve ? "You're approved!" : "Application not approved",
                    approve ? "Welcome to PureEats - go online to start receiving orders."
                            : "Reason: " + reason.trim() + ". Update your details in the app and resubmit.",
                    java.util.Map.of("type", "PARTNER_APPROVAL", "status", detail.getApprovalStatus()));
        });
        log.info("Admin {} {} delivery partner {}", adminUserId, approve ? "approved" : "rejected", id);
        return toResponse(detail);
    }

    @Transactional(readOnly = true)
    public AdminDeliveryGuyResponse getById(Long id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    public AdminDeliveryGuyResponse create(AdminDeliveryGuyRequest request) {
        if (request.email() == null || request.email().isBlank()) {
            log.warn("Rejected delivery partner creation: email is required");
            throw new BadRequestException("Email is required");
        }
        if (userRepository.existsByEmail(request.email())) {
            log.warn("Rejected delivery partner creation: email {} already exists", PiiMaskUtil.maskEmail(request.email()));
            throw new BadRequestException("A user with this email already exists");
        }

        User user = new User();
        user.setName(request.name());
        user.setEmail(request.email());
        user.setPassword(null);
        user.setIsActive(User.STATUS_ACTIVE);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        user = userRepository.save(user);

        DeliveryGuyDetail detail = new DeliveryGuyDetail();
        applyRequest(detail, request);
        detail.setCreatedAt(LocalDateTime.now());
        detail.setUpdatedAt(LocalDateTime.now());
        detail = deliveryGuyDetailRepository.save(detail);

        user.setDeliveryGuyDetailId(detail.getId().intValue());
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        roleService.assignRole(user.getId(), Role.DELIVERY);

        log.info("Created delivery partner {} (user {})", detail.getId(), user.getId());
        return toResponse(detail);
    }

    @Transactional
    public AdminDeliveryGuyResponse update(Long id, AdminDeliveryGuyRequest request) {
        DeliveryGuyDetail detail = findOrThrow(id);
        applyRequest(detail, request);
        detail.setUpdatedAt(LocalDateTime.now());
        deliveryGuyDetailRepository.save(detail);
        // GET /users/me/rider-profile caches per-rider keyed by userId (RiderService), not by this
        // DeliveryGuyDetail id - without this, a rating/vehicle/notifiable change made here (e.g.
        // the admin panel's delivery-guy detail page) stays invisible through that endpoint until
        // the cache's 30-minute TTL expires.
        findLinkedUser(id).ifPresent(user -> {
            riderService.evictProfileCache(user.getId());
            // Deactivating a partner signs them out of the rider app on its next request.
            accountAccessGuard.onAccountChanged(user.getId());
        });
        log.info("Updated delivery partner {}", id);
        return toResponse(detail);
    }

    @Transactional
    public void delete(Long id) {
        DeliveryGuyDetail detail = findOrThrow(id);
        deliveryGuyRestaurantRepository.deleteByDeliveryGuyDetailId(id);
        findLinkedUser(id).ifPresent(user -> {
            user.setDeliveryGuyDetailId(null);
            user.setUpdatedAt(LocalDateTime.now());
            userRepository.save(user);
            riderService.evictProfileCache(user.getId());
            // Their partner account is gone - sign them out of the rider app.
            accountAccessGuard.signOutEverywhere(user.getId());
        });
        deliveryGuyDetailRepository.delete(detail);
        log.info("Deleted delivery partner {}", id);
    }

    @Transactional(readOnly = true)
    public List<Long> assignedRestaurantIds(Long id) {
        return deliveryGuyRestaurantRepository.findByDeliveryGuyDetailId(id).stream()
                .map(DeliveryGuyRestaurant::getRestaurantId).toList();
    }

    @Transactional
    public List<Long> updateAssignedRestaurants(Long id, List<Long> restaurantIds) {
        findOrThrow(id);
        deliveryGuyRestaurantRepository.deleteByDeliveryGuyDetailId(id);
        List<Long> ids = restaurantIds == null ? List.of() : restaurantIds;
        for (Long restaurantId : ids) {
            DeliveryGuyRestaurant link = new DeliveryGuyRestaurant();
            link.setDeliveryGuyDetailId(id);
            link.setRestaurantId(restaurantId);
            link.setCreatedAt(LocalDateTime.now());
            deliveryGuyRestaurantRepository.save(link);
        }
        return ids;
    }

    /** {@code riderUserId} is the rider's User id (matching how order-service records TripDetail.riderId), not the DeliveryGuyDetail id. */
    @Transactional(readOnly = true)
    public List<TripDetailResponse> earningsForRider(Long riderUserId) {
        return tripDetailRepository.findByRiderId(riderUserId.intValue()).stream()
                .sorted((a, b) -> b.getId().compareTo(a.getId()))
                .map(this::toTripResponse)
                .toList();
    }

    private void applyRequest(DeliveryGuyDetail detail, AdminDeliveryGuyRequest request) {
        if (request.name() != null) detail.setName(request.name());
        if (request.age() != null) detail.setAge(String.valueOf(request.age()));
        if (request.gender() != null) detail.setGender(request.gender());
        if (request.vehicleNumber() != null) detail.setVehicleNumber(request.vehicleNumber());
        if (request.description() != null) detail.setDescription(request.description());
        detail.setCommissionRate(request.commissionRate() != null ? request.commissionRate() : orDefault(detail.getCommissionRate(), BigDecimal.ZERO));
        detail.setMaxAcceptDeliveryLimit(request.maxAcceptDeliveryLimit() != null ? request.maxAcceptDeliveryLimit() : orDefault(detail.getMaxAcceptDeliveryLimit(), 1));
        if (request.isNotifiable() != null) detail.setIsNotifiable(request.isNotifiable());
        if (request.isActive() != null) {
            detail.setIsActive(request.isActive());
            if (!request.isActive() && Boolean.TRUE.equals(detail.getIsOnline())) {
                // A deactivated partner stops receiving orders straight away.
                detail.setIsOnline(false);
                detail.setStatusChangedAt(LocalDateTime.now());
                detail.setOfflineReason(DeliveryGuyDetail.OFFLINE_REASON_ADMIN);
            }
        }
        if (request.isOnline() != null && !request.isOnline().equals(detail.getIsOnline())) {
            detail.setIsOnline(request.isOnline());
            detail.setStatusChangedAt(LocalDateTime.now());
            detail.setOfflineReason(request.isOnline() ? null : DeliveryGuyDetail.OFFLINE_REASON_ADMIN);
            if (request.isOnline()) detail.setLastSeenAt(LocalDateTime.now());
            if (detail.getId() != null) {
                riderStatusLogService.record(findLinkedUser(detail.getId()).map(User::getId).orElse(null), request.isOnline(),
                        request.isOnline() ? null : DeliveryGuyDetail.OFFLINE_REASON_ADMIN);
            }
        }
        detail.setRating(request.rating() != null ? request.rating() : orDefault(detail.getRating(), BigDecimal.ZERO));
        if (request.photo() != null) detail.setPhoto(request.photo());
        // Admin corrections to the partner's documents and payout details (any approval status).
        if (request.licenseNumber() != null && !request.licenseNumber().isBlank()) {
            com.pureeats.user.service.RiderKyc.validateLicense(request.licenseNumber());
            com.pureeats.user.service.RiderKyc.applyLicense(detail, request.licenseNumber());
        }
        if (request.idProofNumber() != null && !request.idProofNumber().isBlank()) {
            com.pureeats.user.service.RiderKyc.validateIdProof(request.idProofType(), request.idProofNumber());
            com.pureeats.user.service.RiderKyc.applyIdProof(detail, request.idProofType(), request.idProofNumber());
        }
        if (request.vehicleType() != null && !request.vehicleType().isBlank()) {
            com.pureeats.user.service.RiderKyc.validateVehicle(request.vehicleType(), request.vehicleNumber() != null ? request.vehicleNumber() : detail.getVehicleNumber());
            com.pureeats.user.service.RiderKyc.applyVehicle(detail, request.vehicleType(), request.vehicleNumber());
        }
        if (request.payoutMethod() != null && !request.payoutMethod().isBlank()) {
            com.pureeats.user.service.RiderKyc.validatePayout(request.payoutMethod(), request.bankAccountHolder(), request.bankAccountNumber(), request.bankIfsc(), request.upiId());
            com.pureeats.user.service.RiderKyc.applyPayout(detail, request.payoutMethod(), request.bankAccountHolder(), request.bankAccountNumber(), request.bankIfsc(), request.upiId());
        }
        if (detail.getIsActive() == null) detail.setIsActive(true);
        if (detail.getIsOnline() == null) detail.setIsOnline(false);
        if (detail.getIsNotifiable() == null) detail.setIsNotifiable(true);
    }

    private static <T> T orDefault(T value, T fallback) {
        return value != null ? value : fallback;
    }

    private static Integer parseAge(String age) {
        try {
            return age != null ? Integer.valueOf(age.trim()) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private DeliveryGuyDetail findOrThrow(Long id) {
        return deliveryGuyDetailRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Delivery partner {} not found", id);
                    return new ResourceNotFoundException("Delivery partner not found: " + id);
                });
    }

    private java.util.Optional<User> findLinkedUser(Long deliveryGuyDetailId) {
        return userRepository.findByDeliveryGuyDetailId(deliveryGuyDetailId.intValue());
    }

    private AdminDeliveryGuyResponse toResponse(DeliveryGuyDetail d) {
        User user = findLinkedUser(d.getId()).orElse(null);
        return new AdminDeliveryGuyResponse(d.getId(), user != null ? user.getId() : null, d.getName(),
                parseAge(d.getAge()), d.getGender(), d.getPhoto(), d.getDescription(), d.getVehicleNumber(), d.getCommissionRate(),
                Boolean.TRUE.equals(d.getIsNotifiable()), d.getMaxAcceptDeliveryLimit(), d.getRating(),
                Boolean.TRUE.equals(d.getIsActive()), Boolean.TRUE.equals(d.getIsOnline()), d.getLastLat(), d.getLastLng(),
                d.getLastSeenAt(), d.getOfflineReason(), d.getStatusChangedAt(), d.getCreatedBy(), d.getUpdatedBy(), d.getCreatedAt(), d.getUpdatedAt(),
                user != null ? user.getEmail() : null, user != null ? user.getPhone() : null,
                user != null && User.STATUS_ACTIVE.equals(user.getIsActive()),
                d.getApprovalStatus() != null ? d.getApprovalStatus() : DeliveryGuyDetail.APPROVAL_APPROVED, d.getRejectionReason(),
                d.getApprovalUpdatedAt(), d.getLicenseNumber(), riderService.licensePhotoUrl(d.getId()), d.getIdProofType(),
                d.getIdProofNumber(), d.getVehicleType(), d.getPayoutMethod(), d.getBankAccountHolder(), d.getBankAccountNumber(),
                d.getBankIfsc(), d.getUpiId(), user != null && user.isPhoneVerified(),
                mediaUrlResolver.resolve(d.getPhoto() != null ? d.getPhoto() : user != null ? user.getPhoto() : null));
    }

    private TripDetailResponse toTripResponse(TripDetail t) {
        return new TripDetailResponse(t.getId(), t.getOrderId().longValue(), t.getCustomerId().longValue(),
                t.getRestaurantId().longValue(), t.getRiderId().longValue(),
                t.getDeliveryCollectionId() != null ? t.getDeliveryCollectionId().longValue() : null,
                t.getDistanceTravelled(), t.getRiderEarning(), t.getRestaurantEarning(), t.getCashCollectedFromCustomer(),
                t.getCashOnHold(), t.getIsSettlementDone() != null && t.getIsSettlementDone() == 1, t.getCreatedAt(), t.getUpdatedAt());
    }
}
