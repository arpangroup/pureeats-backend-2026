package com.pureeats.app.deliveryguy.service;

import com.pureeats.app.deliveryguy.dto.AdminDeliveryGuyRequest;
import com.pureeats.app.deliveryguy.dto.AdminDeliveryGuyResponse;
import com.pureeats.app.deliveryguy.dto.TripDetailResponse;
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
import com.pureeats.user.service.RoleService;
import lombok.RequiredArgsConstructor;
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
@Service
@RequiredArgsConstructor
public class AdminDeliveryGuyService {

    private final DeliveryGuyDetailRepository deliveryGuyDetailRepository;
    private final DeliveryGuyRestaurantRepository deliveryGuyRestaurantRepository;
    private final UserRepository userRepository;
    private final RoleService roleService;
    private final TripDetailRepository tripDetailRepository;

    @Transactional(readOnly = true)
    public PageResponse<AdminDeliveryGuyResponse> listPaged(String search, Pageable pageable) {
        Page<DeliveryGuyDetail> page = deliveryGuyDetailRepository.findPage(search, pageable);
        return PageResponse.of(page.getContent().stream().map(this::toResponse).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    @Transactional(readOnly = true)
    public AdminDeliveryGuyResponse getById(Long id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    public AdminDeliveryGuyResponse create(AdminDeliveryGuyRequest request) {
        if (request.email() == null || request.email().isBlank()) {
            throw new BadRequestException("Email is required");
        }
        if (userRepository.existsByEmail(request.email())) {
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

        return toResponse(detail);
    }

    @Transactional
    public AdminDeliveryGuyResponse update(Long id, AdminDeliveryGuyRequest request) {
        DeliveryGuyDetail detail = findOrThrow(id);
        applyRequest(detail, request);
        detail.setUpdatedAt(LocalDateTime.now());
        deliveryGuyDetailRepository.save(detail);
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
        });
        deliveryGuyDetailRepository.delete(detail);
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
        if (request.isActive() != null) detail.setIsActive(request.isActive());
        if (request.isOnline() != null) detail.setIsOnline(request.isOnline());
        detail.setRating(request.rating() != null ? request.rating() : orDefault(detail.getRating(), BigDecimal.ZERO));
        if (request.photo() != null) detail.setPhoto(request.photo());
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
                .orElseThrow(() -> new ResourceNotFoundException("Delivery partner not found: " + id));
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
                d.getLastSeenAt(), d.getCreatedBy(), d.getUpdatedBy(), d.getCreatedAt(), d.getUpdatedAt(),
                user != null ? user.getEmail() : null, user != null ? user.getPhone() : null,
                user != null && User.STATUS_ACTIVE.equals(user.getIsActive()));
    }

    private TripDetailResponse toTripResponse(TripDetail t) {
        return new TripDetailResponse(t.getId(), t.getOrderId().longValue(), t.getCustomerId().longValue(),
                t.getRestaurantId().longValue(), t.getRiderId().longValue(),
                t.getDeliveryCollectionId() != null ? t.getDeliveryCollectionId().longValue() : null,
                t.getDistanceTravelled(), t.getRiderEarning(), t.getRestaurantEarning(), t.getCashCollectedFromCustomer(),
                t.getCashOnHold(), t.getIsSettlementDone() != null && t.getIsSettlementDone() == 1, t.getCreatedAt(), t.getUpdatedAt());
    }
}
