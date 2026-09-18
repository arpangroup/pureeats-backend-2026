package com.pureeats.user.service;

import com.pureeats.domain.common.exception.ConflictException;
import com.pureeats.domain.common.exception.ResourceNotFoundException;
import com.pureeats.domain.entity.DeliveryGuyDetail;
import com.pureeats.domain.entity.User;
import com.pureeats.domain.enums.Role;
import com.pureeats.media.service.MediaAssetService;
import com.pureeats.media.storage.MediaUrlResolver;
import com.pureeats.user.dto.RiderProfileRequest;
import com.pureeats.user.dto.RiderProfileResponse;
import com.pureeats.user.repository.DeliveryGuyDetailRepository;
import com.pureeats.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Self-serve rider onboarding: creates the {@code DeliveryGuyDetail} profile and grants the DELIVERY role. */
@Slf4j
@Service
@RequiredArgsConstructor
public class RiderService {

    private static final BigDecimal DEFAULT_COMMISSION_RATE = BigDecimal.TEN;
    private static final int DEFAULT_MAX_ACCEPT_LIMIT = 3;

    private final UserService userService;
    private final UserRepository userRepository;
    private final DeliveryGuyDetailRepository deliveryGuyDetailRepository;
    private final RoleService roleService;
    private final MediaUrlResolver mediaUrlResolver;
    private final MediaAssetService mediaAssetService;

    private static final String OWNER_TYPE_DELIVERY_GUY = "DELIVERY_GUY";

    /** Cache name for rider/driver-detail lookups - see {@code CacheConfig} in pureeats-app for the swappable (in-memory now, Redis-ready later) {@code CacheManager}. Public: AdminUserService and AdminDeliveryGuyService call {@link #evictProfileCache} after their own writes to the same underlying data (they can't declare their own @CacheEvict on this cache name since Spring resolves the SpEL #userId key against their own method's parameters, which are deliveryGuyDetailId-shaped, not userId-shaped). */
    public static final String RIDER_PROFILES_CACHE = "riderProfiles";

    @Transactional
    @CacheEvict(cacheNames = RIDER_PROFILES_CACHE, key = "#userId")
    public RiderProfileResponse registerAsRider(Long userId, RiderProfileRequest request) {
        log.info("Registering user {} as a delivery rider", userId);
        User user = userService.findUserOrThrow(userId);
        if (user.getDeliveryGuyDetailId() != null) {
            log.warn("Rider registration rejected for user {} - profile already exists", userId);
            throw new ConflictException("A rider profile already exists for this account");
        }

        DeliveryGuyDetail detail = new DeliveryGuyDetail();
        detail.setName(request.name() != null && !request.name().isBlank() ? request.name() : user.getName());
        detail.setAge(request.age());
        detail.setGender(request.gender());
        detail.setDescription(request.description());
        detail.setVehicleNumber(request.vehicleNumber());
        detail.setCommissionRate(DEFAULT_COMMISSION_RATE);
        detail.setMaxAcceptDeliveryLimit(DEFAULT_MAX_ACCEPT_LIMIT);
        detail.setRating(BigDecimal.ZERO);
        detail.setIsNotifiable(true);
        detail.setCreatedAt(LocalDateTime.now());
        detail.setUpdatedAt(LocalDateTime.now());
        detail = deliveryGuyDetailRepository.save(detail);

        user.setDeliveryGuyDetailId(detail.getId().intValue());
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        roleService.assignRole(userId, Role.DELIVERY);
        log.info("Rider profile {} created for user {}", detail.getId(), userId);

        return toResponse(user, detail);
    }

    /** Driver details don't change often (rating updates aside, which are a separate rating-service concern) so this is cached per-rider; {@link #registerAsRider}/{@link #updateProfile}/{@link #uploadPhoto} evict on any write. */
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = RIDER_PROFILES_CACHE, key = "#userId")
    public RiderProfileResponse getProfile(Long userId) {
        User user = userService.findUserOrThrow(userId);
        DeliveryGuyDetail detail = resolveOwnDetail(user);
        return toResponse(user, detail);
    }

    /** Partial update of the rider's own editable text fields, post-onboarding - unlike {@link #registerAsRider}, this edits the EXISTING DeliveryGuyDetail rather than rejecting because one already exists. Null/blank request fields leave the current value as-is. */
    @Transactional
    @CacheEvict(cacheNames = RIDER_PROFILES_CACHE, key = "#userId")
    public RiderProfileResponse updateProfile(Long userId, RiderProfileRequest request) {
        User user = userService.findUserOrThrow(userId);
        DeliveryGuyDetail detail = resolveOwnDetail(user);
        if (request.name() != null && !request.name().isBlank()) detail.setName(request.name());
        if (request.vehicleNumber() != null && !request.vehicleNumber().isBlank()) detail.setVehicleNumber(request.vehicleNumber());
        if (request.age() != null) detail.setAge(request.age());
        if (request.gender() != null) detail.setGender(request.gender());
        if (request.description() != null) detail.setDescription(request.description());
        detail.setUpdatedAt(LocalDateTime.now());
        detail.setUpdatedBy(userId);
        deliveryGuyDetailRepository.save(detail);
        log.info("Rider {} updated their own profile", userId);
        return toResponse(user, detail);
    }

    /** Mirrors {@code AdminUserService#uploadPhoto}'s pattern - a photo is a file, handled as its own multipart action, entirely separate from the JSON profile-fields update above. Writes BOTH DeliveryGuyDetail.photo (what this app and order-tracking's riderAssignedData read) AND User.photo (what the admin panel's generic "Edit user" page reads) - these are two independent columns for historical reasons (see #toResponse's own fallback comment), and a rider uploading their own photo here should not leave the admin's view of them stale. */
    @Transactional
    @CacheEvict(cacheNames = RIDER_PROFILES_CACHE, key = "#userId")
    public RiderProfileResponse uploadPhoto(Long userId, MultipartFile file) {
        User user = userService.findUserOrThrow(userId);
        DeliveryGuyDetail detail = resolveOwnDetail(user);
        String storageKey = mediaAssetService.upload(file, OWNER_TYPE_DELIVERY_GUY, detail.getId(), userId).storageKey();
        detail.setPhoto(storageKey);
        detail.setUpdatedAt(LocalDateTime.now());
        detail.setUpdatedBy(userId);
        deliveryGuyDetailRepository.save(detail);
        user.setPhoto(storageKey);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
        log.info("Rider {} updated their own profile photo", userId);
        return toResponse(user, detail);
    }

    /**
     * Called by every OTHER writer of data this cache serves - AdminUserService (generic user
     * edit/photo), AdminDeliveryGuyService (admin delivery-partner edit, the write that left a
     * rating change invisible via this endpoint until the 30-minute cache TTL expired), and
     * ProfileContactChangeService (the OTP-verified phone/email change flow every app's Edit
     * Profile screen uses, including this one's own EditRiderProfilePage - the write that left a
     * newly-added phone number invisible here). Each of those methods lives in a different service
     * (different SpEL parameter shapes - deliveryGuyDetailId, not userId), so they can't declare
     * their own {@code @CacheEvict(cacheNames = RIDER_PROFILES_CACHE, key = "#userId")} directly;
     * this gives them a userId-keyed eviction to call explicitly instead. A no-op for a userId this
     * cache never held (not a rider, or already evicted) - Spring's cache abstraction treats
     * evicting a missing key as a normal no-op, not an error.
     */
    @CacheEvict(cacheNames = RIDER_PROFILES_CACHE, key = "#userId")
    public void evictProfileCache(Long userId) {
    }

    private DeliveryGuyDetail resolveOwnDetail(User user) {
        if (user.getDeliveryGuyDetailId() == null) {
            log.warn("Rider profile lookup failed for user {} - no rider profile", user.getId());
            throw new ResourceNotFoundException("No rider profile for this account");
        }
        return deliveryGuyDetailRepository.findById(user.getDeliveryGuyDetailId().longValue())
                .orElseThrow(() -> {
                    log.warn("Rider profile {} referenced by user {} not found", user.getDeliveryGuyDetailId(), user.getId());
                    return new ResourceNotFoundException("Rider profile not found");
                });
    }

    private RiderProfileResponse toResponse(User user, DeliveryGuyDetail detail) {
        // Two independent photo columns exist for historical reasons: DeliveryGuyDetail.photo
        // (set by this app's own upload endpoint, or the admin's delivery-guy-specific edit form)
        // and User.photo (set by the admin's generic "Users" edit page, AdminUserController#uploadPhoto).
        // Prefer the rider-specific one but fall back to the user-identity one so a photo set via
        // either admin path actually shows up here.
        String photoKey = detail.getPhoto() != null ? detail.getPhoto() : user.getPhoto();
        return new RiderProfileResponse(detail.getId(), user.getId(), detail.getName(), user.getEmail(), user.getPhone(),
                mediaUrlResolver.resolve(photoKey), detail.getVehicleNumber(), detail.getAge(), detail.getGender(),
                detail.getDescription(), detail.getCommissionRate(), detail.getMaxAcceptDeliveryLimit(), detail.getRating(),
                Boolean.TRUE.equals(detail.getIsNotifiable()), Boolean.TRUE.equals(detail.getIsOnline()),
                Boolean.TRUE.equals(detail.getIsActive()));
    }
}
