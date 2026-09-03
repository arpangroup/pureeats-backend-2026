package com.pureeats.catalog.service;

import com.pureeats.catalog.dto.*;
import com.pureeats.catalog.geo.DistanceCalculator;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

@Slf4j
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
    private final DistanceCalculator distanceCalculator;

    /** Name of the cache backing every {@code @Cacheable} method below - see {@code CacheConfig} in pureeats-app for the (in-memory now, Redis-ready later) {@code CacheManager}. */
    static final String RESTAURANTS_CACHE = "restaurants";
    private static final double KM_PER_DEGREE_LAT = 111.32;

    private static final String IMAGE_OWNER_TYPE = "RESTAURANT";
    private static final String COVER_IMAGE_OWNER_TYPE = "RESTAURANT_COVER";
    private static final long MAX_IMAGE_BYTES = 2L * 1024 * 1024;
    private static final int MAX_IMAGES = 5;

    @Transactional(readOnly = true)
    public List<RestaurantSummaryResponse> listActive() {
        return cachedActiveRestaurants().stream().map(this::toSummary).toList();
    }

    /**
     * Active+accepted restaurants rarely change (a store owner toggling availability, or admin
     * acceptance/edit, are the only writers) so the raw entity list is cached whole here - both
     * {@link #listActive} and {@link #findNearby} reuse this exact cached list rather than
     * re-querying per request; {@link #findNearby} then filters/sorts by distance in-process
     * against the cached rows. Every write path that could affect this set ({@link #patchAsAdmin},
     * {@link #setEnabled}, {@link #createAsAdmin}, ...) evicts the whole {@value #RESTAURANTS_CACHE}
     * cache rather than trying to surgically patch one entry.
     */
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = RESTAURANTS_CACHE, key = "'active'")
    public List<Restaurant> cachedActiveRestaurants() {
        return restaurantRepository.findByIsActiveTrueAndIsAcceptedTrue();
    }

    /**
     * Nearby-restaurant search: reuses the cached active-restaurant list (see
     * {@link #cachedActiveRestaurants}) instead of hitting the DB per request, applies a cheap
     * bounding-box pre-filter (avoids running the full {@link DistanceCalculator} - potentially a
     * paid API call under the "google" provider - against restaurants that are obviously out of
     * range), then computes the exact distance only for the survivors and filters/sorts by it.
     * Each restaurant's own {@code deliveryRadius} is the range check unless the caller passes an
     * explicit {@code radiusKm} override.
     */
    @Transactional(readOnly = true)
    public List<RestaurantSummaryResponse> findNearby(String customerLat, String customerLng, BigDecimal radiusKmOverride) {
        double lat;
        double lng;
        try {
            lat = Double.parseDouble(customerLat);
            lng = Double.parseDouble(customerLng);
        } catch (NumberFormatException | NullPointerException e) {
            throw new BadRequestException("Valid latitude and longitude are required");
        }

        double maxRadiusKm = radiusKmOverride != null ? radiusKmOverride.doubleValue() : maxKnownDeliveryRadiusKm();
        double latDeltaDeg = maxRadiusKm / KM_PER_DEGREE_LAT;
        double lngDeltaDeg = maxRadiusKm / (KM_PER_DEGREE_LAT * Math.max(Math.cos(Math.toRadians(lat)), 0.1));

        return cachedActiveRestaurants().stream()
                .filter(r -> withinBoundingBox(r, lat, lng, latDeltaDeg, lngDeltaDeg))
                .map(r -> Map.entry(r, distanceCalculator.distanceKm(r.getLatitude(), r.getLongitude(), customerLat, customerLng)))
                .filter(entry -> {
                    BigDecimal effectiveRadius = radiusKmOverride != null ? radiusKmOverride : entry.getKey().getDeliveryRadius();
                    return effectiveRadius == null || entry.getValue().compareTo(effectiveRadius) <= 0;
                })
                .sorted(Comparator.comparing(Map.Entry::getValue))
                .map(entry -> toSummary(entry.getKey()))
                .toList();
    }

    private boolean withinBoundingBox(Restaurant r, double lat, double lng, double latDeltaDeg, double lngDeltaDeg) {
        try {
            double rLat = Double.parseDouble(r.getLatitude());
            double rLng = Double.parseDouble(r.getLongitude());
            return Math.abs(rLat - lat) <= latDeltaDeg && Math.abs(rLng - lng) <= lngDeltaDeg;
        } catch (NumberFormatException | NullPointerException e) {
            return false;
        }
    }

    /** Bounding-box sizing fallback for a plain "nearby" search with no explicit radius override - wide enough that no restaurant's own (typically much smaller) delivery radius is pre-filtered away, capped so a bad/missing radius on one row can't blow up the box for everyone. */
    private double maxKnownDeliveryRadiusKm() {
        return 30.0;
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
    @CacheEvict(cacheNames = RESTAURANTS_CACHE, allEntries = true)
    public RestaurantDetailResponse create(Long ownerUserId, RestaurantCreateRequest request) {
        log.info("Owner {} registering restaurant '{}' (pending acceptance)", ownerUserId, request.name());
        Restaurant restaurant = restaurantRepository.save(buildRestaurant(request, false));

        RestaurantUser link = new RestaurantUser();
        link.setUserId(ownerUserId);
        link.setRestaurantId(restaurant.getId());
        link.setCreatedAt(LocalDateTime.now());
        link.setUpdatedAt(LocalDateTime.now());
        restaurantUserRepository.save(link);

        roleService.assignRole(ownerUserId, Role.STORE_OWNER);

        log.info("Restaurant {} registered by owner {}, awaiting admin acceptance", restaurant.getId(), ownerUserId);
        return toDetail(restaurant);
    }

    /** Admin-created restaurants have no self-onboarding owner to link and are accepted immediately, unlike store-owner self-onboarding. */
    @Transactional
    @CacheEvict(cacheNames = RESTAURANTS_CACHE, allEntries = true)
    public RestaurantDetailResponse createAsAdmin(RestaurantCreateRequest request) {
        log.info("Admin creating restaurant '{}' (accepted immediately)", request.name());
        Restaurant restaurant = restaurantRepository.save(buildRestaurant(request, true));
        log.info("Restaurant {} created by admin", restaurant.getId());
        return toDetail(restaurant);
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
    @CacheEvict(cacheNames = RESTAURANTS_CACHE, allEntries = true)
    public RestaurantDetailResponse update(Long ownerUserId, Long restaurantId, RestaurantUpdateRequest request) {
        log.info("Owner {} updating restaurant {}", ownerUserId, restaurantId);
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
    @CacheEvict(cacheNames = RESTAURANTS_CACHE, allEntries = true)
    public RestaurantDetailResponse patchAsAdmin(Long callerUserId, Long restaurantId, RestaurantPatchRequest request, Role callerRole) {
        log.info("User {} (role {}) patching restaurant {}", callerUserId, callerRole, restaurantId);
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
        applyField(restaurant.getId(), "certificate", restaurant.getCertificate(), request.certificate(), isPrivileged, callerUserId, restaurant::setCertificate);
        applyField(restaurant.getId(), "isPureveg", restaurant.getIsPureveg(), request.isPureveg(), isPrivileged, callerUserId, restaurant::setIsPureveg);
        applyField(restaurant.getId(), "locationId", restaurant.getLocationId(), request.locationId(), isPrivileged, callerUserId, restaurant::setLocationId);
        applyField(restaurant.getId(), "latitude", restaurant.getLatitude(), request.latitude(), isPrivileged, callerUserId, restaurant::setLatitude);
        applyField(restaurant.getId(), "longitude", restaurant.getLongitude(), request.longitude(), isPrivileged, callerUserId, restaurant::setLongitude);
        applyField(restaurant.getId(), "restaurantCharges", restaurant.getRestaurantCharges(), request.restaurantCharges(), isPrivileged, callerUserId, restaurant::setRestaurantCharges);
        applyField(restaurant.getId(), "deliveryCharges", restaurant.getDeliveryCharges(), request.deliveryCharges(), isPrivileged, callerUserId, restaurant::setDeliveryCharges);
        applyField(restaurant.getId(), "deliveryRadius", restaurant.getDeliveryRadius(), request.deliveryRadius(), isPrivileged, callerUserId, restaurant::setDeliveryRadius);
        applyField(restaurant.getId(), "minOrderPrice", restaurant.getMinOrderPrice(), request.minOrderPrice(), isPrivileged, callerUserId, restaurant::setMinOrderPrice);
        if (request.deliveryType() != null) {
            applyField(restaurant.getId(), "deliveryType", deliveryTypeLabel(restaurant.getDeliveryType()), request.deliveryType(),
                    isPrivileged, callerUserId, label -> restaurant.setDeliveryType(deliveryTypeCode(label)));
        }
        applyField(restaurant.getId(), "deliveryChargeType", restaurant.getDeliveryChargeType(), request.deliveryChargeType(), isPrivileged, callerUserId, restaurant::setDeliveryChargeType);
        applyField(restaurant.getId(), "baseDeliveryCharge", restaurant.getBaseDeliveryCharge(), request.baseDeliveryCharge(), isPrivileged, callerUserId, restaurant::setBaseDeliveryCharge);
        applyField(restaurant.getId(), "baseDeliveryDistance", restaurant.getBaseDeliveryDistance(), request.baseDeliveryDistance(), isPrivileged, callerUserId, restaurant::setBaseDeliveryDistance);
        applyField(restaurant.getId(), "extraDeliveryCharge", restaurant.getExtraDeliveryCharge(), request.extraDeliveryCharge(), isPrivileged, callerUserId, restaurant::setExtraDeliveryCharge);
        applyField(restaurant.getId(), "extraDeliveryDistance", restaurant.getExtraDeliveryDistance(), request.extraDeliveryDistance(), isPrivileged, callerUserId, restaurant::setExtraDeliveryDistance);
        applyField(restaurant.getId(), "isSchedulable", restaurant.getIsSchedulable(), request.isSchedulable(), isPrivileged, callerUserId, restaurant::setIsSchedulable);
        applyField(restaurant.getId(), "isNotifiable", restaurant.getIsNotifiable(), request.isNotifiable(), isPrivileged, callerUserId, restaurant::setIsNotifiable);
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
            log.warn("User {} attempted to update admin-only field '{}' on restaurant {} without privilege", callerUserId, fieldName, restaurantId);
            throw new ForbiddenException("Only ADMIN or SUPER_ADMIN can update '" + fieldName + "'");
        }
        setter.accept(newValue);
        restaurantAuditLogService.record(restaurantId, fieldName, oldValue, newValue, callerUserId);
    }

    @Transactional
    @CacheEvict(cacheNames = RESTAURANTS_CACHE, allEntries = true)
    public void deleteAsAdmin(Long restaurantId) {
        log.info("Admin deleting restaurant {}", restaurantId);
        restaurantRepository.delete(findOrThrow(restaurantId));
    }

    /** Replaces the restaurant's single main/cover image (distinct from the gallery - separate owner type, no 5-image cap). */
    @Transactional
    @CacheEvict(cacheNames = RESTAURANTS_CACHE, allEntries = true)
    public RestaurantImageResponse uploadCoverImage(Long restaurantId, MultipartFile file, Long uploadedBy) {
        log.info("Uploading cover image for restaurant {} by user {}", restaurantId, uploadedBy);
        Restaurant restaurant = findOrThrow(restaurantId);
        String oldImage = restaurant.getImage();
        var uploaded = mediaAssetService.upload(file, COVER_IMAGE_OWNER_TYPE, restaurantId, uploadedBy, MAX_IMAGE_BYTES);
        restaurant.setImage(uploaded.storageKey());
        restaurant.setUpdatedAt(LocalDateTime.now());
        restaurantRepository.save(restaurant);
        restaurantAuditLogService.record(restaurantId, "image", oldImage, uploaded.storageKey(), uploadedBy);
        return new RestaurantImageResponse(uploaded.id(), uploaded.url());
    }

    @Transactional
    public RestaurantImageResponse uploadImage(Long restaurantId, MultipartFile file, Long uploadedBy) {
        log.info("Uploading gallery image for restaurant {} by user {}", restaurantId, uploadedBy);
        findOrThrow(restaurantId);
        long existingCount = mediaAssetService.countForOwner(IMAGE_OWNER_TYPE, restaurantId);
        if (existingCount >= MAX_IMAGES) {
            log.warn("Restaurant {} rejected gallery upload - already has {} images (max {})", restaurantId, existingCount, MAX_IMAGES);
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
        log.info("Deleting gallery image {} from restaurant {}", mediaId, restaurantId);
        mediaAssetService.delete(IMAGE_OWNER_TYPE, restaurantId, mediaId);
    }

    @Transactional
    @CacheEvict(cacheNames = RESTAURANTS_CACHE, allEntries = true)
    public void setEnabled(Long ownerUserId, Long restaurantId, boolean enabled) {
        Restaurant restaurant = assertOwnership(ownerUserId, restaurantId);
        restaurant.setIsActive(enabled);
        restaurant.setUpdatedAt(LocalDateTime.now());
        restaurantRepository.save(restaurant);
        log.info("Restaurant {} {} by owner {}", restaurantId, enabled ? "enabled" : "disabled", ownerUserId);
    }

    @Transactional(readOnly = true)
    public DeliveryAreaCheckResponse checkDeliveryArea(Long restaurantId, String latitude, String longitude) {
        Restaurant restaurant = findOrThrow(restaurantId);
        BigDecimal distanceKm = distanceCalculator.distanceKm(restaurant.getLatitude(), restaurant.getLongitude(), latitude, longitude);
        boolean isOperational = restaurant.getIsActive() && restaurant.getIsAccepted()
                && distanceKm.compareTo(restaurant.getDeliveryRadius()) <= 0;
        log.debug("Delivery area check for restaurant {}: distance {} km, operational {}", restaurantId, distanceKm, isOperational);
        return new DeliveryAreaCheckResponse(isOperational, distanceKm.doubleValue());
    }

    /** Restaurant ownership is many-to-many via {@code restaurant_user} - an owner may run several restaurants. */
    public Restaurant assertOwnership(Long ownerUserId, Long restaurantId) {
        restaurantUserRepository.findByUserIdAndRestaurantId(ownerUserId, restaurantId)
                .orElseThrow(() -> {
                    log.warn("Owner {} attempted to access restaurant {} they do not own", ownerUserId, restaurantId);
                    return new ForbiddenException("This restaurant does not belong to you");
                });
        return findOrThrow(restaurantId);
    }

    private Restaurant findOrThrow(Long id) {
        return restaurantRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Restaurant {} not found", id);
                    return new ResourceNotFoundException("Restaurant not found: " + id);
                });
    }

    @Transactional(readOnly = true)
    public boolean exists(Long id) {
        return id != null && restaurantRepository.existsById(id);
    }

    /** Batch lookup for cross-restaurant listings (e.g. the Home page's recommended-items feed) that need restaurant context for a set of ids without an N+1 fetch per item. */
    @Transactional(readOnly = true)
    public Map<Long, RestaurantSummaryResponse> summariesByIds(List<Long> ids) {
        return restaurantRepository.findAllById(ids).stream()
                .collect(java.util.stream.Collectors.toMap(Restaurant::getId, this::toSummary));
    }

    private static String slugify(String name) {
        return name.trim().toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    RestaurantSummaryResponse toSummary(Restaurant r) {
        return new RestaurantSummaryResponse(r.getId(), r.getName(), r.getSlug(), mediaUrlResolver.resolve(r.getImage()), r.getRating(),
                r.getDeliveryTime(), r.getPriceRange(), Boolean.TRUE.equals(r.getIsPureveg()),
                Boolean.TRUE.equals(r.getIsActive()), Boolean.TRUE.equals(r.getIsAccepted()),
                r.getMinOrderPrice(), r.getDeliveryCharges(),
                r.getOpeningTime(), r.getClosingTime(), Boolean.TRUE.equals(r.getIsFeatured()));
    }

    private RestaurantDetailResponse toDetail(Restaurant r) {
        return new RestaurantDetailResponse(r.getId(), r.getName(), r.getDescription(), r.getSlug(),
                r.getContactNumber(), r.getOpeningTime(), r.getClosingTime(), mediaUrlResolver.resolve(r.getImage()), r.getRating(),
                r.getDeliveryTime(), r.getPriceRange(), Boolean.TRUE.equals(r.getIsPureveg()), r.getAddress(),
                r.getPincode(), r.getLandmark(), r.getCertificate(), r.getLocationId(), r.getLatitude(), r.getLongitude(), r.getRestaurantCharges(),
                r.getDeliveryCharges(), r.getDeliveryRadius(), r.getMinOrderPrice(), deliveryTypeLabel(r.getDeliveryType()),
                r.getDeliveryChargeType(), r.getBaseDeliveryCharge(), r.getBaseDeliveryDistance(),
                r.getExtraDeliveryCharge(), r.getExtraDeliveryDistance(), Boolean.TRUE.equals(r.getIsSchedulable()),
                Boolean.TRUE.equals(r.getIsNotifiable()), Boolean.TRUE.equals(r.getIsActive()), Boolean.TRUE.equals(r.getIsAccepted()),
                Boolean.TRUE.equals(r.getIsFeatured()), Boolean.TRUE.equals(r.getIsAcceptCod()),
                Boolean.TRUE.equals(r.getAutoAcceptable()));
    }

    private static final Map<String, Integer> DELIVERY_TYPE_TO_INT = Map.of("self-pickup", 0, "delivery", 1, "both", 2);
    private static final Map<Integer, String> DELIVERY_TYPE_TO_LABEL = Map.of(0, "self-pickup", 1, "delivery", 2, "both");

    private static Integer deliveryTypeCode(String label) {
        return DELIVERY_TYPE_TO_INT.getOrDefault(label, 1);
    }

    private static String deliveryTypeLabel(Integer code) {
        return DELIVERY_TYPE_TO_LABEL.getOrDefault(code, "delivery");
    }
}
