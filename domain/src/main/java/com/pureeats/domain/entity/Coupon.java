package com.pureeats.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "coupons")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Coupon {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "name")
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "discount_type", nullable = false)
    private String discountType;

    @Column(name = "discount", nullable = false)
    private String discount;

    @Column(name = "expiry_date")
    private LocalDateTime expiryDate;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "restaurant_id")
    private Integer restaurantId;

    @Column(name = "min_order_amount", nullable = false)
    private BigDecimal minOrderAmount;

    @Column(name = "upto_amount")
    private String uptoAmount;

    @Column(name = "total_coupon", nullable = false)
    private Integer totalCoupon;

    @Column(name = "count", nullable = false)
    private Integer count;

    @Column(name = "max_count", nullable = false)
    private Integer maxCount;

    @Column(name = "first_order_only")
    private Boolean firstOrderOnly;

    /** Who created this coupon - an admin (global coupons) or a store owner (their own coupons). Null for coupons created before this was tracked. */
    @Column(name = "created_by")
    private Long createdBy;
}
