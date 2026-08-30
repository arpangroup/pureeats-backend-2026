package com.pureeats.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "unique_order_id", nullable = false)
    private String uniqueOrderId;

    @Column(name = "orderstatus_id", nullable = false)
    private Integer orderstatusId;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    /** Snapshot of the coupon's display NAME at order-placement time (not the code - see {@link #couponCode}). */
    @Column(name = "coupon_name")
    private String couponName;

    @Column(name = "coupon_code")
    private String couponCode;

    @Column(name = "discount_amount")
    private BigDecimal discountAmount;

    /** JSON snapshot of how this order's charges were computed - see {@code PricingBreakdown}. */
    @Lob
    @Column(name = "pricing_breakdown")
    private String pricingBreakdown;

    @Lob
    @Column(name = "location")
    private String location;

    @Column(name = "address", nullable = false)
    private String address;

    @Column(name = "tax")
    private BigDecimal tax;

    @Column(name = "restaurant_charge")
    private BigDecimal restaurantCharge;

    @Column(name = "delivery_charge")
    private BigDecimal deliveryCharge;

    @Column(name = "driver_tip_amount")
    private BigDecimal driverTipAmount;

    @Column(name = "total", nullable = false)
    private BigDecimal total;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "payment_mode", nullable = false)
    private String paymentMode;

    @Lob
    @Column(name = "order_comment")
    private String orderComment;

    @Column(name = "restaurant_id")
    private Integer restaurantId;

    @Lob
    @Column(name = "transaction_id")
    private String transactionId;

    @Column(name = "delivery_type", nullable = false)
    private Integer deliveryType;

    @Column(name = "delivery_pin", nullable = false)
    private String deliveryPin;

    @Column(name = "payable", nullable = false)
    private BigDecimal payable;

    @Column(name = "prepare_time")
    private Integer prepareTime;

    @Column(name = "order_from", nullable = false)
    private String orderFrom;
}
