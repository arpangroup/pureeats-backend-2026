package com.pureeats.user.service;

import com.pureeats.domain.common.exception.ConflictException;
import com.pureeats.domain.entity.DeliveryGuyDetail;
import com.pureeats.domain.entity.User;
import com.pureeats.domain.enums.Role;
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

    /** Cache name for rider/driver-detail lookups - see {@code CacheConfig} in pureeats-app for the swappable (in-memory now, Redis-ready later) {@code CacheManager}. */
    static final String RIDER_PROFILES_CACHE = "riderProfiles";

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
        detail.setName(user.getName());
        detail.setAge(request.age());
        detail.setGender(request.gender());
        detail.setPhoto(request.photo());
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

        return toResponse(detail);
    }

    /** Driver details don't change often (rating updates aside, which are a separate rating-service concern) so this is cached per-rider; {@link #registerAsRider} evicts on (re-)creation. */
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = RIDER_PROFILES_CACHE, key = "#userId")
    public RiderProfileResponse getProfile(Long userId) {
        User user = userService.findUserOrThrow(userId);
        if (user.getDeliveryGuyDetailId() == null) {
            log.warn("Rider profile lookup failed for user {} - no rider profile", userId);
            throw new com.pureeats.domain.common.exception.ResourceNotFoundException("No rider profile for this account");
        }
        DeliveryGuyDetail detail = deliveryGuyDetailRepository.findById(user.getDeliveryGuyDetailId().longValue())
                .orElseThrow(() -> {
                    log.warn("Rider profile {} referenced by user {} not found", user.getDeliveryGuyDetailId(), userId);
                    return new com.pureeats.domain.common.exception.ResourceNotFoundException("Rider profile not found");
                });
        return toResponse(detail);
    }

    private RiderProfileResponse toResponse(DeliveryGuyDetail detail) {
        return new RiderProfileResponse(detail.getId(), detail.getVehicleNumber(), detail.getCommissionRate(),
                detail.getMaxAcceptDeliveryLimit(), detail.getRating(), Boolean.TRUE.equals(detail.getIsNotifiable()));
    }
}
