package com.pureeats.catalog.service;

import com.pureeats.catalog.dto.*;
import com.pureeats.catalog.repository.RestaurantRepository;
import com.pureeats.catalog.repository.RestaurantUserRepository;
import com.pureeats.domain.common.exception.ForbiddenException;
import com.pureeats.domain.common.exception.ResourceNotFoundException;
import com.pureeats.domain.common.response.PageResponse;
import com.pureeats.domain.entity.Restaurant;
import com.pureeats.domain.entity.RestaurantUser;
import com.pureeats.domain.common.exception.BadRequestException;
import com.pureeats.domain.enums.Role;
import com.pureeats.media.service.MediaAssetService;
import com.pureeats.media.storage.MediaUrlResolver;
import com.pureeats.user.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class RestaurantService {

    /** Only ADMIN/SUPER_ADMIN may change these via {@link #patchAsAdmin} - add/remove field names here to retune. */
    private static final Set<String> ADMIN_ONLY_FIELDS = Set.of(
            "name", "commissionRate", "isActive", "isAccepted", "autoAcceptable", "isFeatured");

    private final RestaurantRepository restaurantRepository;
    private final RestaurantUserRepository restaurantUserRepository;
    private final RoleService roleService;
    private final MediaUrlResolver mediaUrlResolver;
    private final RestaurantAuditLogService restaurantAuditLogService;
    private final MediaAssetService mediaAssetService;

    private static final String IMAGE_OWNER_TYPE = "RESTAURANT";
    private static final long MAX_IMAGE_BYTES = 2L * 1024 * 1024;
    private static final int MAX_IMAGES = 5;

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

    /** Admin listing - every restaurant regardless of active/accepted status. */
    @Transactional(readOnly = true)
    public PageResponse<RestaurantSummaryResponse> listPaged(String search, Pageable pageable) {
        Page<Restaurant> page = restaurantRepository.findPage(search, pageable);
        return PageResponse.of(page.getContent().stream().map(this::toSummary).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    @Transactional(readOnly = true)
    public List<RestaurantSummaryResponse> listOwnedBy(Long userId) {
        List<Long> restaurantIds = restaurantUserRepository.findByUserId(userId).stream()
                .map(RestaurantUser::getRestaurantId).toList();
        return restaurantRepository.findAllById(restaurantIds).stream().map(this::toSummary).toList();
    }

    @Transactional
    public RestaurantDetailResponse create(Long ownerUserId, RestaurantCreateRequest request) {
        Restaurant restaurant = restaurantRepository.save(buildRestaurant(request, false));

        RestaurantUser link = new RestaurantUser();
        link.setUserId(ownerUserId);
        link.setRestaurantId(restaurant.getId());
        link.setCreatedAt(LocalDateTime.now());
        link.setUpdatedAt(LocalDateTime.now());
        restaurantUserRepository.save(link);

        roleService.assignRole(ownerUserId, Role.STORE_OWNER);

        return toDetail(restaurant);
    }

    /** Admin-created restaurants have no self-onboarding owner to link and are accepted immediately, unlike store-owner self-onboarding. */
    @Transactional
    public RestaurantDetailResponse createAsAdmin(RestaurantCreateRequest request) {
        return toDetail(restaurantRepository.save(buildRestaurant(request, true)));
    }

    private Restaurant buildRestaurant(RestaurantCreateRequest request, boolean isAccepted) {
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
        restaurant.setIsAccepted(isAccepted);
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
        return restaurant;
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

    /**
     * Partial update, admin-facing - unlike {@link #update}, not scoped to a single owner and
     * additionally allows the {@link #ADMIN_ONLY_FIELDS} (which {@link RestaurantUpdateRequest}
     * deliberately never exposes to store owners at all). Every changed field is diffed and
     * recorded via {@link RestaurantAuditLogService}.
     */
    @Transactional
    public RestaurantDetailResponse patchAsAdmin(Long callerUserId, Long restaurantId, RestaurantPatchRequest request, Role callerRole) {
        Restaurant restaurant = findOrThrow(restaurantId);
        boolean isPrivileged = callerRole == Role.ADMIN || callerRole == Role.SUPER_ADMIN;

        applyField(restaurant.getId(), "name", restaurant.getName(), request.name(), isPrivileged, callerUserId, restaurant::setName);
        applyField(restaurant.getId(), "description", restaurant.getDescription(), request.description(), isPrivileged, callerUserId, restaurant::setDescription);
        applyField(restaurant.getId(), "contactNumber", restaurant.getContactNumber(), request.contactNumber(), isPrivileged, callerUserId, restaurant::setContactNumber);
        applyField(restaurant.getId(), "openingTime", restaurant.getOpeningTime(), request.openingTime(), isPrivileged, callerUserId, restaurant::setOpeningTime);
        applyField(restaurant.getId(), "closingTime", restaurant.getClosingTime(), request.closingTime(), isPrivileged, callerUserId, restaurant::setClosingTime);
        applyField(restaurant.getId(), "address", restaurant.getAddress(), request.address(), isPrivileged, callerUserId, restaurant::setAddress);
        applyField(restaurant.getId(), "pincode", restaurant.getPincode(), request.pincode(), isPrivileged, callerUserId, restaurant::setPincode);
        applyField(restaurant.getId(), "landmark", restaurant.getLandmark(), request.landmark(), isPrivileged, callerUserId, restaurant::setLandmark);
        applyField(restaurant.getId(), "deliveryCharges", restaurant.getDeliveryCharges(), request.deliveryCharges(), isPrivileged, callerUserId, restaurant::setDeliveryCharges);
        applyField(restaurant.getId(), "deliveryRadius", restaurant.getDeliveryRadius(), request.deliveryRadius(), isPrivileged, callerUserId, restaurant::setDeliveryRadius);
        applyField(restaurant.getId(), "minOrderPrice", restaurant.getMinOrderPrice(), request.minOrderPrice(), isPrivileged, callerUserId, restaurant::setMinOrderPrice);
        applyField(restaurant.getId(), "isAcceptCod", restaurant.getIsAcceptCod(), request.isAcceptCod(), isPrivileged, callerUserId, restaurant::setIsAcceptCod);
        applyField(restaurant.getId(), "autoAcceptable", restaurant.getAutoAcceptable(), request.autoAcceptable(), isPrivileged, callerUserId, restaurant::setAutoAcceptable);
        applyField(restaurant.getId(), "isActive", restaurant.getIsActive(), request.isActive(), isPrivileged, callerUserId, restaurant::setIsActive);
        applyField(restaurant.getId(), "isAccepted", restaurant.getIsAccepted(), request.isAccepted(), isPrivileged, callerUserId, restaurant::setIsAccepted);
        applyField(restaurant.getId(), "isFeatured", restaurant.getIsFeatured(), request.isFeatured(), isPrivileged, callerUserId, restaurant::setIsFeatured);
        applyField(restaurant.getId(), "commissionRate", restaurant.getCommissionRate(), request.commissionRate(), isPrivileged, callerUserId, restaurant::setCommissionRate);

        restaurant.setUpdatedAt(LocalDateTime.now());
        restaurantRepository.save(restaurant);
        return toDetail(restaurant);
    }

    /** Applies + audit-logs one field, if present and actually changed; rejects it outright if it's admin-only and the caller isn't. */
    private <T> void applyField(Long restaurantId, String fieldName, T oldValue, T newValue,
                                 boolean isPrivileged, Long callerUserId, Consumer<T> setter) {
        if (newValue == null || newValue.equals(oldValue)) {
            return;
        }
        if (ADMIN_ONLY_FIELDS.contains(fieldName) && !isPrivileged) {
            throw new ForbiddenException("Only ADMIN or SUPER_ADMIN can update '" + fieldName + "'");
        }
        setter.accept(newValue);
        restaurantAuditLogService.record(restaurantId, fieldName, oldValue, newValue, callerUserId);
    }

    @Transactional
    public void deleteAsAdmin(Long restaurantId) {
        restaurantRepository.delete(findOrThrow(restaurantId));
    }

    @Transactional
    public RestaurantImageResponse uploadImage(Long restaurantId, MultipartFile file, Long uploadedBy) {
        findOrThrow(restaurantId);
        if (mediaAssetService.countForOwner(IMAGE_OWNER_TYPE, restaurantId) >= MAX_IMAGES) {
            throw new BadRequestException("A store can have at most " + MAX_IMAGES + " images");
        }
        var uploaded = mediaAssetService.upload(file, IMAGE_OWNER_TYPE, restaurantId, uploadedBy, MAX_IMAGE_BYTES);
        return new RestaurantImageResponse(uploaded.id(), uploaded.url());
    }

    @Transactional(readOnly = true)
    public List<RestaurantImageResponse> listImages(Long restaurantId) {
        return mediaAssetService.listForOwner(IMAGE_OWNER_TYPE, restaurantId).stream()
                .map(asset -> new RestaurantImageResponse(asset.getId(), mediaUrlResolver.resolve(asset.getStorageKey())))
                .toList();
    }

    @Transactional
    public void deleteImage(Long restaurantId, Long mediaId) {
        mediaAssetService.delete(IMAGE_OWNER_TYPE, restaurantId, mediaId);
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

    @Transactional(readOnly = true)
    public boolean exists(Long id) {
        return id != null && restaurantRepository.existsById(id);
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

    RestaurantSummaryResponse toSummary(Restaurant r) {
        return new RestaurantSummaryResponse(r.getId(), r.getName(), r.getSlug(), mediaUrlResolver.resolve(r.getImage()), r.getRating(),
                r.getDeliveryTime(), r.getPriceRange(), Boolean.TRUE.equals(r.getIsPureveg()),
                Boolean.TRUE.equals(r.getIsActive()), Boolean.TRUE.equals(r.getIsAccepted()),
                r.getMinOrderPrice(), r.getDeliveryCharges());
    }

    private RestaurantDetailResponse toDetail(Restaurant r) {
        return new RestaurantDetailResponse(r.getId(), r.getName(), r.getDescription(), r.getSlug(),
                r.getContactNumber(), r.getOpeningTime(), r.getClosingTime(), mediaUrlResolver.resolve(r.getImage()), r.getRating(),
                r.getDeliveryTime(), r.getPriceRange(), Boolean.TRUE.equals(r.getIsPureveg()), r.getAddress(),
                r.getPincode(), r.getLandmark(), r.getLatitude(), r.getLongitude(), r.getRestaurantCharges(),
                r.getDeliveryCharges(), r.getDeliveryRadius(), r.getMinOrderPrice(),
                Boolean.TRUE.equals(r.getIsActive()), Boolean.TRUE.equals(r.getIsAccepted()),
                Boolean.TRUE.equals(r.getIsFeatured()), Boolean.TRUE.equals(r.getIsAcceptCod()),
                Boolean.TRUE.equals(r.getAutoAcceptable()));
    }
}
