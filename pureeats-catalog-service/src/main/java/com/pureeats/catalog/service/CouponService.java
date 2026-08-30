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
import com.pureeats.domain.common.exception.ResourceNotFoundException;
import com.pureeats.domain.common.response.PageResponse;
import com.pureeats.domain.entity.Coupon;
import com.pureeats.domain.entity.CouponUsage;
import com.pureeats.domain.enums.DiscountType;
import com.pureeats.user.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
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

    /** Best-effort lookup by code, for callers (e.g. order-service) that just want the current display name/type - not scoped to active-only. */
    @Transactional(readOnly = true)
    public java.util.Optional<CouponResponse> findByCode(String code) {
        return couponRepository.findByCodeIgnoreCase(code).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public CouponResponse getById(Long id) {
        return toResponse(couponRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Coupon not found: " + id)));
    }

    @Transactional
    public CouponResponse create(CouponCreateRequest request) {
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
        coupon.setCreatedAt(LocalDateTime.now());
        coupon.setUpdatedAt(LocalDateTime.now());
        return toResponse(couponRepository.save(coupon));
    }

    /** Admin-facing full update. Unlike {@link #create}, lets the caller set {@code maxCount} and {@code isActive} directly. */
    @Transactional
    public CouponResponse update(Long id, CouponUpdateRequest request) {
        Coupon coupon = couponRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Coupon not found: " + id));
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
        return toResponse(couponRepository.save(coupon));
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

    @Transactional
    public void delete(Long id) {
        Coupon coupon = couponRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Coupon not found: " + id));
        couponRepository.delete(coupon);
    }

    /**
     * Validates a coupon code and computes the discount, WITHOUT recording usage yet -
     * usage is only recorded by {@link #recordUsage} once the order is actually placed. Lenient
     * about {@code firstOrderOnly} (treats the caller as eligible) since this is just an estimate -
     * the real check happens at redemption, where the caller actually knows the user's order history.
     */
    @Transactional(readOnly = true)
    public CouponApplyResponse preview(CouponApplyRequest request) {
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

        return new CouponRedemptionResult(coupon.getId(), coupon.getCode(), coupon.getName(), discount, calculator.waivesDeliveryCharge());
    }

    private Coupon validate(String code, Integer restaurantId, BigDecimal orderAmount, boolean isFirstOrder) {
        Coupon coupon = couponRepository.findByCodeIgnoreCaseAndIsActiveTrue(code)
                .orElseThrow(() -> new BadRequestException("Invalid or inactive coupon code"));

        if (coupon.getExpiryDate() != null && coupon.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("This coupon has expired");
        }
        boolean isGlobal = coupon.getRestaurantId() == null || coupon.getRestaurantId() == GLOBAL_RESTAURANT_ID;
        if (!isGlobal && !coupon.getRestaurantId().equals(restaurantId)) {
            throw new BadRequestException("This coupon is not valid for this restaurant");
        }
        if (coupon.getCount() != null && coupon.getTotalCoupon() != null && coupon.getCount() >= coupon.getTotalCoupon()) {
            throw new BadRequestException("This coupon has reached its usage limit");
        }
        if (orderAmount.compareTo(coupon.getMinOrderAmount()) < 0) {
            throw new BadRequestException("Minimum order amount for this coupon is " + coupon.getMinOrderAmount());
        }
        if (Boolean.TRUE.equals(coupon.getFirstOrderOnly()) && !isFirstOrder) {
            throw new BadRequestException("This coupon is only valid on your first order");
        }
        return coupon;
    }

    private DiscountCalculator calculatorFor(Coupon coupon) {
        DiscountType type = DiscountType.valueOf(coupon.getDiscountType());
        DiscountCalculator calculator = calculatorsByType.get(type);
        if (calculator == null) {
            throw new BadRequestException("Unsupported discount type: " + type);
        }
        return calculator;
    }

    private CouponResponse toResponse(Coupon c) {
        return new CouponResponse(c.getId(), c.getName(), c.getDescription(), c.getCode(),
                toWireValue(DiscountType.valueOf(c.getDiscountType())), new BigDecimal(c.getDiscount()), c.getMinOrderAmount(),
                new BigDecimal(c.getUptoAmount()), c.getExpiryDate() != null ? c.getExpiryDate().toLocalDate() : null,
                Boolean.TRUE.equals(c.getIsActive()), c.getRestaurantId(), Boolean.TRUE.equals(c.getFirstOrderOnly()),
                c.getTotalCoupon(), c.getCount(), c.getMaxCount());
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
