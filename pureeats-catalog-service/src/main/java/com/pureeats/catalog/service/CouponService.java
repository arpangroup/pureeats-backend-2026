package com.pureeats.catalog.service;

import com.pureeats.catalog.dto.CouponApplyRequest;
import com.pureeats.catalog.dto.CouponApplyResponse;
import com.pureeats.catalog.dto.CouponCreateRequest;
import com.pureeats.catalog.dto.CouponResponse;
import com.pureeats.catalog.repository.CouponRepository;
import com.pureeats.catalog.repository.CouponUsageRepository;
import com.pureeats.domain.common.exception.BadRequestException;
import com.pureeats.domain.entity.Coupon;
import com.pureeats.domain.entity.CouponUsage;
import com.pureeats.domain.enums.DiscountType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CouponService {

    private static final int GLOBAL_RESTAURANT_ID = 0;

    private final CouponRepository couponRepository;
    private final CouponUsageRepository couponUsageRepository;

    @Transactional(readOnly = true)
    public List<CouponResponse> listAvailable(Integer restaurantId) {
        return couponRepository.findByIsActiveTrueAndRestaurantIdIn(List.of(GLOBAL_RESTAURANT_ID, restaurantId))
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public CouponResponse create(CouponCreateRequest request) {
        Coupon coupon = new Coupon();
        coupon.setName(request.name());
        coupon.setDescription(request.description());
        coupon.setCode(request.code());
        coupon.setDiscountType(request.discountType().name());
        coupon.setDiscount(request.discount().toPlainString());
        coupon.setMinOrderAmount(request.minOrderAmount());
        coupon.setUptoAmount(request.uptoAmount().toPlainString());
        coupon.setExpiryDate(request.expiryDate());
        coupon.setRestaurantId(request.restaurantId() != null ? request.restaurantId() : GLOBAL_RESTAURANT_ID);
        coupon.setIsActive(true);
        coupon.setTotalCoupon(request.totalCoupon());
        coupon.setCount(0);
        coupon.setMaxCount(request.totalCoupon());
        coupon.setCreatedAt(LocalDateTime.now());
        coupon.setUpdatedAt(LocalDateTime.now());
        return toResponse(couponRepository.save(coupon));
    }

    /**
     * Validates a coupon code and computes the discount, WITHOUT recording usage yet -
     * usage is only recorded by {@link #recordUsage} once the order is actually placed.
     */
    @Transactional(readOnly = true)
    public CouponApplyResponse preview(CouponApplyRequest request) {
        Coupon coupon = validate(request.code(), request.restaurantId(), request.orderAmount());
        BigDecimal discount = computeDiscount(coupon, request.orderAmount());
        return new CouponApplyResponse(coupon.getId(), coupon.getCode(), discount,
                request.orderAmount().subtract(discount).setScale(2, RoundingMode.HALF_UP));
    }

    /** Called by order-service at order-placement time to atomically re-validate + record one usage. */
    @Transactional
    public BigDecimal recordUsage(String code, Integer restaurantId, BigDecimal orderAmount, Integer userId) {
        Coupon coupon = validate(code, restaurantId, orderAmount);
        BigDecimal discount = computeDiscount(coupon, orderAmount);

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

        return discount;
    }

    private Coupon validate(String code, Integer restaurantId, BigDecimal orderAmount) {
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
        return coupon;
    }

    private BigDecimal computeDiscount(Coupon coupon, BigDecimal orderAmount) {
        BigDecimal discountValue = new BigDecimal(coupon.getDiscount());
        DiscountType type = DiscountType.valueOf(coupon.getDiscountType());

        BigDecimal discount = type == DiscountType.PERCENTAGE
                ? orderAmount.multiply(discountValue).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                : discountValue;

        BigDecimal cap = new BigDecimal(coupon.getUptoAmount());
        if (discount.compareTo(cap) > 0) {
            discount = cap;
        }
        return discount;
    }

    private CouponResponse toResponse(Coupon c) {
        return new CouponResponse(c.getId(), c.getName(), c.getDescription(), c.getCode(),
                DiscountType.valueOf(c.getDiscountType()), new BigDecimal(c.getDiscount()), c.getMinOrderAmount(),
                new BigDecimal(c.getUptoAmount()), c.getExpiryDate(), Boolean.TRUE.equals(c.getIsActive()),
                c.getRestaurantId());
    }
}
