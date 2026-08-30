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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Self-serve rider onboarding: creates the {@code DeliveryGuyDetail} profile and grants the DELIVERY role. */
@Service
@RequiredArgsConstructor
public class RiderService {

    private static final BigDecimal DEFAULT_COMMISSION_RATE = BigDecimal.TEN;
    private static final int DEFAULT_MAX_ACCEPT_LIMIT = 3;

    private final UserService userService;
    private final UserRepository userRepository;
    private final DeliveryGuyDetailRepository deliveryGuyDetailRepository;
    private final RoleService roleService;

    @Transactional
    public RiderProfileResponse registerAsRider(Long userId, RiderProfileRequest request) {
        User user = userService.findUserOrThrow(userId);
        if (user.getDeliveryGuyDetailId() != null) {
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

        return toResponse(detail);
    }

    @Transactional(readOnly = true)
    public RiderProfileResponse getProfile(Long userId) {
        User user = userService.findUserOrThrow(userId);
        if (user.getDeliveryGuyDetailId() == null) {
            throw new com.pureeats.domain.common.exception.ResourceNotFoundException("No rider profile for this account");
        }
        DeliveryGuyDetail detail = deliveryGuyDetailRepository.findById(user.getDeliveryGuyDetailId().longValue())
                .orElseThrow(() -> new com.pureeats.domain.common.exception.ResourceNotFoundException("Rider profile not found"));
        return toResponse(detail);
    }

    private RiderProfileResponse toResponse(DeliveryGuyDetail detail) {
        return new RiderProfileResponse(detail.getId(), detail.getVehicleNumber(), detail.getCommissionRate(),
                detail.getMaxAcceptDeliveryLimit(), detail.getRating(), Boolean.TRUE.equals(detail.getIsNotifiable()));
    }
}
