package com.pureeats.catalog.service;

import com.pureeats.catalog.dto.*;
import com.pureeats.catalog.repository.RestaurantRepository;
import com.pureeats.catalog.repository.RestaurantUserRepository;
import com.pureeats.domain.common.exception.ForbiddenException;
import com.pureeats.domain.common.exception.ResourceNotFoundException;
import com.pureeats.domain.entity.Restaurant;
import com.pureeats.domain.entity.RestaurantUser;
import com.pureeats.domain.enums.Role;
import com.pureeats.user.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RestaurantService {

    private final RestaurantRepository restaurantRepository;
    private final RestaurantUserRepository restaurantUserRepository;
    private final RoleService roleService;

    @Transactional(readOnly = true)
    public List<RestaurantSummaryResponse> listActive() {
        return restaurantRepository.findByIsActiveTrueAndIsAcceptedTrue().stream().map(this::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public List<RestaurantSummaryResponse> search(String query) {
        return restaurantRepository.search(query).stream().map(this::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public RestaurantDetailResponse getBySlug(String slug) {
        return toDetail(restaurantRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found: " + slug)));
    }

    @Transactional(readOnly = true)
    public RestaurantDetailResponse getById(Long id) {
        return toDetail(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<RestaurantSummaryResponse> listOwnedBy(Long userId) {
        List<Long> restaurantIds = restaurantUserRepository.findByUserId(userId).stream()
                .map(RestaurantUser::getRestaurantId).toList();
        return restaurantRepository.findAllById(restaurantIds).stream().map(this::toSummary).toList();
    }

    @Transactional
    public RestaurantDetailResponse create(Long ownerUserId, RestaurantCreateRequest request) {
        Restaurant restaurant = new Restaurant();
        restaurant.setName(request.name());
        restaurant.setDescription(request.description());
        restaurant.setContactNumber(request.contactNumber());
        restaurant.setOpeningTime(request.openingTime());
        restaurant.setClosingTime(request.closingTime());
        restaurant.setImage(request.image());
        restaurant.setAddress(request.address());
        restaurant.setPincode(request.pincode());
        restaurant.setLandmark(request.landmark());
        restaurant.setLatitude(request.latitude());
        restaurant.setLongitude(request.longitude());
        restaurant.setIsPureveg(request.isPureveg());
        restaurant.setDeliveryCharges(request.deliveryCharges());
        restaurant.setDeliveryRadius(request.deliveryRadius());
        restaurant.setMinOrderPrice(request.minOrderPrice());
        restaurant.setIsAcceptCod(request.isAcceptCod());

        restaurant.setSlug(slugify(request.name()) + "-" + UUID.randomUUID().toString().substring(0, 6));
        restaurant.setSku(UUID.randomUUID().toString().substring(0, 12).toUpperCase());
        restaurant.setIsActive(true);
        restaurant.setIsAccepted(false);
        restaurant.setIsFeatured(false);
        restaurant.setCommissionRate(BigDecimal.TEN);
        restaurant.setRestaurantCharges(BigDecimal.ZERO);
        restaurant.setDeliveryType(0);
        restaurant.setDeliveryChargeType("FIXED");
        restaurant.setIsSchedulable(false);
        restaurant.setAutoAcceptable(false);
        restaurant.setIsNotifiable(true);
        restaurant.setCreatedAt(LocalDateTime.now());
        restaurant.setUpdatedAt(LocalDateTime.now());
        restaurant = restaurantRepository.save(restaurant);

        RestaurantUser link = new RestaurantUser();
        link.setUserId(ownerUserId);
        link.setRestaurantId(restaurant.getId());
        link.setCreatedAt(LocalDateTime.now());
        link.setUpdatedAt(LocalDateTime.now());
        restaurantUserRepository.save(link);

        roleService.assignRole(ownerUserId, Role.STORE_OWNER);

        return toDetail(restaurant);
    }

    @Transactional
    public RestaurantDetailResponse update(Long ownerUserId, Long restaurantId, RestaurantUpdateRequest request) {
        Restaurant restaurant = assertOwnership(ownerUserId, restaurantId);
        restaurant.setName(request.name());
        restaurant.setDescription(request.description());
        restaurant.setContactNumber(request.contactNumber());
        restaurant.setOpeningTime(request.openingTime());
        restaurant.setClosingTime(request.closingTime());
        restaurant.setImage(request.image());
        restaurant.setAddress(request.address());
        restaurant.setPincode(request.pincode());
        restaurant.setLandmark(request.landmark());
        restaurant.setDeliveryCharges(request.deliveryCharges());
        restaurant.setDeliveryRadius(request.deliveryRadius());
        restaurant.setMinOrderPrice(request.minOrderPrice());
        restaurant.setIsAcceptCod(request.isAcceptCod());
        restaurant.setAutoAcceptable(request.autoAcceptable());
        restaurant.setUpdatedAt(LocalDateTime.now());
        restaurantRepository.save(restaurant);
        return toDetail(restaurant);
    }

    @Transactional
    public void setEnabled(Long ownerUserId, Long restaurantId, boolean enabled) {
        Restaurant restaurant = assertOwnership(ownerUserId, restaurantId);
        restaurant.setIsActive(enabled);
        restaurant.setUpdatedAt(LocalDateTime.now());
        restaurantRepository.save(restaurant);
    }

    @Transactional(readOnly = true)
    public DeliveryAreaCheckResponse checkDeliveryArea(Long restaurantId, String latitude, String longitude) {
        Restaurant restaurant = findOrThrow(restaurantId);
        double distanceKm = haversineKm(
                Double.parseDouble(restaurant.getLatitude()), Double.parseDouble(restaurant.getLongitude()),
                Double.parseDouble(latitude), Double.parseDouble(longitude));
        boolean isOperational = restaurant.getIsActive() && restaurant.getIsAccepted()
                && distanceKm <= restaurant.getDeliveryRadius().doubleValue();
        return new DeliveryAreaCheckResponse(isOperational, distanceKm);
    }

    /** Restaurant ownership is many-to-many via {@code restaurant_user} - an owner may run several restaurants. */
    public Restaurant assertOwnership(Long ownerUserId, Long restaurantId) {
        restaurantUserRepository.findByUserIdAndRestaurantId(ownerUserId, restaurantId)
                .orElseThrow(() -> new ForbiddenException("This restaurant does not belong to you"));
        return findOrThrow(restaurantId);
    }

    private Restaurant findOrThrow(Long id) {
        return restaurantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found: " + id));
    }

    private static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double earthRadiusKm = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadiusKm * c;
    }

    private static String slugify(String name) {
        return name.trim().toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    private RestaurantSummaryResponse toSummary(Restaurant r) {
        return new RestaurantSummaryResponse(r.getId(), r.getName(), r.getSlug(), r.getImage(), r.getRating(),
                r.getDeliveryTime(), r.getPriceRange(), Boolean.TRUE.equals(r.getIsPureveg()),
                Boolean.TRUE.equals(r.getIsActive()), Boolean.TRUE.equals(r.getIsAccepted()),
                r.getMinOrderPrice(), r.getDeliveryCharges());
    }

    private RestaurantDetailResponse toDetail(Restaurant r) {
        return new RestaurantDetailResponse(r.getId(), r.getName(), r.getDescription(), r.getSlug(),
                r.getContactNumber(), r.getOpeningTime(), r.getClosingTime(), r.getImage(), r.getRating(),
                r.getDeliveryTime(), r.getPriceRange(), Boolean.TRUE.equals(r.getIsPureveg()), r.getAddress(),
                r.getPincode(), r.getLandmark(), r.getLatitude(), r.getLongitude(), r.getRestaurantCharges(),
                r.getDeliveryCharges(), r.getDeliveryRadius(), r.getMinOrderPrice(),
                Boolean.TRUE.equals(r.getIsActive()), Boolean.TRUE.equals(r.getIsAccepted()),
                Boolean.TRUE.equals(r.getIsFeatured()), Boolean.TRUE.equals(r.getIsAcceptCod()),
                Boolean.TRUE.equals(r.getAutoAcceptable()));
    }
}
