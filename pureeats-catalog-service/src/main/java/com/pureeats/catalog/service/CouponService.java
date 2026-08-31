package com.pureeats.catalog.service;

import com.pureeats.catalog.dto.CouponApplyRequest;
import com.pureeats.catalog.dto.CouponApplyResponse;
import com.pureeats.catalog.dto.CouponCreateRequest;
import com.pureeats.catalog.dto.CouponRedemptionResult;
import com.pureeats.catalog.dto.CouponResponse;
import com.pureeats.catalog.dto.CouponUpdateRequest;
import com.pureeats.catalog.dto.CouponUsageResponse;
import com.pureeats.catalog.repository.CouponRepository;
import com.pureeats.catalog.repository.CouponUsageRepository;
import com.pureeats.catalog.repository.RestaurantRepository;
import com.pureeats.catalog.service.discount.DiscountCalculator;
import com.pureeats.domain.common.exception.BadRequestException;
import com.pureeats.domain.common.exception.ForbiddenException;
import com.pureeats.domain.common.exception.ResourceNotFoundException;
import com.pureeats.domain.common.response.PageResponse;
import com.pureeats.domain.entity.Coupon;
import com.pureeats.domain.entity.CouponUsage;
import com.pureeats.domain.enums.DiscountType;
import com.pureeats.user.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponService {

    private static final int GLOBAL_RESTAURANT_ID = 0;

    private final CouponRepository couponRepository;
    private final CouponUsageRepository couponUsageRepository;
    private final UserRepository userRepository;
    private final RestaurantRepository restaurantRepository;
    private final List<DiscountCalculator> discountCalculators;

    private Map<DiscountType, DiscountCalculator> calculatorsByType;

    @PostConstruct
    void indexCalculators() {
        calculatorsByType = new EnumMap<>(DiscountType.class);
        discountCalculators.forEach(c -> calculatorsByType.put(c.type(), c));
        log.info("Indexed {} discount calculators: {}", calculatorsByType.size(), calculatorsByType.keySet());
    }

    @Transactional(readOnly = true)
    public List<CouponResponse> listAvailable(Integer restaurantId) {
        return couponRepository.findByIsActiveTrueAndRestaurantIdIn(List.of(GLOBAL_RESTAURANT_ID, restaurantId))
                .stream().map(this::toResponse).toList();
    }

    /** Admin listing - every coupon, optionally filtered by a name/code search. */
    @Transactional(readOnly = true)
    public PageResponse<CouponResponse> listPaged(String search, Pageable pageable) {
        Page<Coupon> page = couponRepository.findPage(search, pageable);
        return PageResponse.of(page.getContent().stream().map(this::toResponse).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    /** Store-owner listing - only coupons scoped to one of their restaurants. */
    @Transactional(readOnly = true)
    public PageResponse<CouponResponse> listForRestaurant(Integer restaurantId, String search, Pageable pageable) {
        Page<Coupon> page = couponRepository.findByRestaurantIdPage(restaurantId, search, pageable);
        return PageResponse.of(page.getContent().stream().map(this::toResponse).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    /** Best-effort lookup by code, for callers (e.g. order-service) that just want the current display name/type - not scoped to active-only. */
    @Transactional(readOnly = true)
    public java.util.Optional<CouponResponse> findByCode(String code) {
        return couponRepository.findByCodeIgnoreCase(code).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public CouponResponse getById(Long id) {
        return toResponse(couponRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Coupon {} not found", id);
                    return new ResourceNotFoundException("Coupon not found: " + id);
                }));
    }

    @Transactional
    public CouponResponse create(CouponCreateRequest request, Long createdBy) {
        log.info("Creating coupon '{}' (code {}) for restaurant {} by user {}", request.name(), request.code(),
                request.restaurantId(), createdBy);
        Coupon coupon = new Coupon();
        coupon.setName(request.name());
        coupon.setDescription(request.description());
        coupon.setCode(request.code());
        coupon.setDiscountType(toDiscountType(request.discountType()).name());
        coupon.setDiscount(orZero(request.discount()).toPlainString());
        coupon.setMinOrderAmount(request.minOrderAmount());
        coupon.setUptoAmount(orZero(request.uptoAmount()).toPlainString());
        coupon.setExpiryDate(request.expiryDate() != null ? request.expiryDate().atTime(23, 59, 59) : null);
        coupon.setRestaurantId(request.restaurantId() != null ? request.restaurantId() : GLOBAL_RESTAURANT_ID);
        coupon.setIsActive(true);
        coupon.setTotalCoupon(request.totalCoupon());
        coupon.setCount(0);
        coupon.setMaxCount(request.totalCoupon());
        coupon.setFirstOrderOnly(Boolean.TRUE.equals(request.firstOrderOnly()));
        coupon.setCreatedBy(createdBy);
        coupon.setCreatedAt(LocalDateTime.now());
        coupon.setUpdatedAt(LocalDateTime.now());
        Coupon saved = couponRepository.save(coupon);
        log.info("Coupon {} (code {}) created", saved.getId(), saved.getCode());
        return toResponse(saved);
    }

    /** Admin-facing full update - not ownership-scoped (an admin may edit anyone's coupon), and lets the caller set {@code maxCount}/{@code isActive} directly. */
    @Transactional
    public CouponResponse update(Long id, CouponUpdateRequest request) {
        log.info("Admin updating coupon {}", id);
        Coupon coupon = couponRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Admin update failed - coupon {} not found", id);
                    return new ResourceNotFoundException("Coupon not found: " + id);
                });
        applyUpdate(coupon, request);
        return toResponse(couponRepository.save(coupon));
    }

    /** Store-owner update - only the coupon's creator may edit it. */
    @Transactional
    public CouponResponse updateAsOwner(Long ownerUserId, Long id, CouponUpdateRequest request) {
        log.info("Owner {} updating coupon {}", ownerUserId, id);
        Coupon coupon = findOwnedOrThrow(ownerUserId, id);
        applyUpdate(coupon, request);
        return toResponse(couponRepository.save(coupon));
    }

    private void applyUpdate(Coupon coupon, CouponUpdateRequest request) {
        coupon.setName(request.name());
        coupon.setDescription(request.description());
        coupon.setCode(request.code());
        coupon.setDiscountType(toDiscountType(request.discountType()).name());
        coupon.setDiscount(orZero(request.discount()).toPlainString());
        coupon.setMinOrderAmount(request.minOrderAmount());
        coupon.setUptoAmount(orZero(request.uptoAmount()).toPlainString());
        coupon.setExpiryDate(request.expiryDate() != null ? request.expiryDate().atTime(23, 59, 59) : null);
        coupon.setRestaurantId(request.restaurantId() != null ? request.restaurantId() : GLOBAL_RESTAURANT_ID);
        coupon.setTotalCoupon(request.totalCoupon());
        coupon.setMaxCount(request.maxCount());
        coupon.setIsActive(request.isActive());
        coupon.setFirstOrderOnly(Boolean.TRUE.equals(request.firstOrderOnly()));
        coupon.setUpdatedAt(LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public List<CouponUsageResponse> listUsages(Long couponId) {
        return couponUsageRepository.findByCouponIdOrderByCreatedAtDesc(couponId.intValue()).stream()
                .map(u -> new CouponUsageResponse(u.getId(),
                        userRepository.findById(u.getUserId().longValue()).map(user -> user.getName()).orElse("Unknown"),
                        restaurantRepository.findById(u.getResturantId().longValue()).map(r -> r.getName()).orElse("Unknown"),
                        u.getCouponUsed(), u.getCreatedAt()))
                .toList();
    }

    /** Admin-only delete - the caller already had to hold ADMIN/SUPER_ADMIN to reach this (enforced at the URL/controller layer). */
    @Transactional
    public void delete(Long id) {
        log.info("Admin deleting coupon {}", id);
        Coupon coupon = couponRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Admin delete failed - coupon {} not found", id);
                    return new ResourceNotFoundException("Coupon not found: " + id);
                });
        couponRepository.delete(coupon);
    }

    /** Store-owner delete - only the coupon's creator may delete it. */
    @Transactional
    public void deleteAsOwner(Long ownerUserId, Long id) {
        log.info("Owner {} deleting coupon {}", ownerUserId, id);
        couponRepository.delete(findOwnedOrThrow(ownerUserId, id));
    }

    private Coupon findOwnedOrThrow(Long ownerUserId, Long id) {
        Coupon coupon = couponRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Coupon {} not found", id);
                    return new ResourceNotFoundException("Coupon not found: " + id);
                });
        if (coupon.getCreatedBy() == null || !coupon.getCreatedBy().equals(ownerUserId)) {
            log.warn("Owner {} attempted to manage coupon {} created by {}", ownerUserId, id, coupon.getCreatedBy());
            throw new ForbiddenException("You can only manage coupons you created");
        }
        return coupon;
    }

    /**
     * Validates a coupon code and computes the discount, WITHOUT recording usage yet -
     * usage is only recorded by {@link #recordUsage} once the order is actually placed. Lenient
     * about {@code firstOrderOnly} (treats the caller as eligible) since this is just an estimate -
     * the real check happens at redemption, where the caller actually knows the user's order history.
     */
    @Transactional(readOnly = true)
    public CouponApplyResponse preview(CouponApplyRequest request) {
        log.debug("Previewing coupon '{}' for restaurant {} order amount {}", request.code(), request.restaurantId(), request.orderAmount());
        Coupon coupon = validate(request.code(), request.restaurantId(), request.orderAmount(), true);
        BigDecimal discount = calculatorFor(coupon).calculateDiscount(coupon, request.orderAmount());
        return new CouponApplyResponse(coupon.getId(), coupon.getCode(), discount,
                request.orderAmount().subtract(discount).setScale(2, RoundingMode.HALF_UP));
    }

    /**
     * Called by order-service at order-placement time to atomically re-validate + record one
     * usage. {@code isFirstOrder} is sourced from the caller (order-service owns OrderRepository;
     * catalog-service can't depend on it without creating a circular module dependency).
     */
    @Transactional
    public CouponRedemptionResult recordUsage(String code, Integer restaurantId, BigDecimal orderAmount, Integer userId, boolean isFirstOrder) {
        log.info("Recording coupon usage: code '{}' restaurant {} user {} orderAmount {}", code, restaurantId, userId, orderAmount);
        Coupon coupon = validate(code, restaurantId, orderAmount, isFirstOrder);
        DiscountCalculator calculator = calculatorFor(coupon);
        BigDecimal discount = calculator.calculateDiscount(coupon, orderAmount);

        coupon.setCount(coupon.getCount() + 1);
        coupon.setUpdatedAt(LocalDateTime.now());
        couponRepository.save(coupon);

        CouponUsage usage = new CouponUsage();
        usage.setCouponId(coupon.getId().intValue());
        usage.setUserId(userId);
        usage.setResturantId(restaurantId);
        usage.setCouponUsed(1);
        usage.setCreatedAt(LocalDateTime.now());
        usage.setUpdatedAt(LocalDateTime.now());
        couponUsageRepository.save(usage);

        log.info("Coupon {} (code '{}') redeemed by user {}: discount {}", coupon.getId(), coupon.getCode(), userId, discount);
        return new CouponRedemptionResult(coupon.getId(), coupon.getCode(), coupon.getName(), discount, calculator.waivesDeliveryCharge());
    }

    private Coupon validate(String code, Integer restaurantId, BigDecimal orderAmount, boolean isFirstOrder) {
        Coupon coupon = couponRepository.findByCodeIgnoreCaseAndIsActiveTrue(code)
                .orElseThrow(() -> {
                    log.warn("Coupon validation failed: code '{}' is invalid or inactive", code);
                    return new BadRequestException("Invalid or inactive coupon code");
                });

        if (coupon.getExpiryDate() != null && coupon.getExpiryDate().isBefore(LocalDateTime.now())) {
            log.warn("Coupon {} (code '{}') rejected - expired at {}", coupon.getId(), code, coupon.getExpiryDate());
            throw new BadRequestException("This coupon has expired");
        }
        boolean isGlobal = coupon.getRestaurantId() == null || coupon.getRestaurantId() == GLOBAL_RESTAURANT_ID;
        if (!isGlobal && !coupon.getRestaurantId().equals(restaurantId)) {
            log.warn("Coupon {} (code '{}') rejected - not valid for restaurant {} (scoped to {})",
                    coupon.getId(), code, restaurantId, coupon.getRestaurantId());
            throw new BadRequestException("This coupon is not valid for this restaurant");
        }
        if (coupon.getCount() != null && coupon.getTotalCoupon() != null && coupon.getCount() >= coupon.getTotalCoupon()) {
            log.warn("Coupon {} (code '{}') rejected - usage limit reached ({}/{})",
                    coupon.getId(), code, coupon.getCount(), coupon.getTotalCoupon());
            throw new BadRequestException("This coupon has reached its usage limit");
        }
        if (orderAmount.compareTo(coupon.getMinOrderAmount()) < 0) {
            log.warn("Coupon {} (code '{}') rejected - order amount {} below minimum {}",
                    coupon.getId(), code, orderAmount, coupon.getMinOrderAmount());
            throw new BadRequestException("Minimum order amount for this coupon is " + coupon.getMinOrderAmount());
        }
        if (Boolean.TRUE.equals(coupon.getFirstOrderOnly()) && !isFirstOrder) {
            log.warn("Coupon {} (code '{}') rejected - first-order-only, caller is not on their first order", coupon.getId(), code);
            throw new BadRequestException("This coupon is only valid on your first order");
        }
        return coupon;
    }

    private DiscountCalculator calculatorFor(Coupon coupon) {
        DiscountType type = DiscountType.valueOf(coupon.getDiscountType());
        DiscountCalculator calculator = calculatorsByType.get(type);
        if (calculator == null) {
            log.warn("No discount calculator registered for type {} (coupon {})", type, coupon.getId());
            throw new BadRequestException("Unsupported discount type: " + type);
        }
        return calculator;
    }

    private CouponResponse toResponse(Coupon c) {
        return new CouponResponse(c.getId(), c.getName(), c.getDescription(), c.getCode(),
                toWireValue(DiscountType.valueOf(c.getDiscountType())), new BigDecimal(c.getDiscount()), c.getMinOrderAmount(),
                new BigDecimal(c.getUptoAmount()), c.getExpiryDate() != null ? c.getExpiryDate().toLocalDate() : null,
                Boolean.TRUE.equals(c.getIsActive()), c.getRestaurantId(), Boolean.TRUE.equals(c.getFirstOrderOnly()),
                c.getTotalCoupon(), c.getCount(), c.getMaxCount(), c.getCreatedBy());
    }

    /** Wire format is "flat"/"percentage"/"free_delivery" (matching the React admin UI's naming), not the Java enum's own constant names. */
    public static DiscountType toDiscountType(String wireValue) {
        if ("percentage".equalsIgnoreCase(wireValue)) {
            return DiscountType.PERCENTAGE;
        }
        if ("free_delivery".equalsIgnoreCase(wireValue) || "freedelivery".equalsIgnoreCase(wireValue)) {
            return DiscountType.FREE_DELIVERY;
        }
        if ("flat".equalsIgnoreCase(wireValue) || "amount".equalsIgnoreCase(wireValue)) {
            return DiscountType.AMOUNT;
        }
        log.warn("Unknown discount type requested: '{}'", wireValue);
        throw new BadRequestException("Unknown discount type: " + wireValue);
    }

    private static String toWireValue(DiscountType type) {
        return switch (type) {
            case PERCENTAGE -> "percentage";
            case FREE_DELIVERY -> "free_delivery";
            case AMOUNT -> "flat";
        };
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
