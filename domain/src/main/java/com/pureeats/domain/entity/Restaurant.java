package com.pureeats.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "restaurants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Restaurant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "contact_number", nullable = false)
    private String contactNumber;

    @Column(name = "opening_time", nullable = false)
    private LocalTime openingTime;

    @Column(name = "closing_time", nullable = false)
    private LocalTime closingTime;

    @Column(name = "location_id")
    private String locationId;

    @Column(name = "image")
    private String image;

    @Column(name = "rating")
    private String rating;

    @Column(name = "delivery_time")
    private String deliveryTime;

    @Column(name = "price_range")
    private String priceRange;

    @Column(name = "is_pureveg", nullable = false)
    private Boolean isPureveg;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Lob
    @Column(name = "slug")
    private String slug;

    @Column(name = "placeholder_image")
    private String placeholderImage;

    @Column(name = "latitude")
    private String latitude;

    @Column(name = "longitude")
    private String longitude;

    @Column(name = "certificate")
    private String certificate;

    @Column(name = "restaurant_charges")
    private BigDecimal restaurantCharges;

    @Column(name = "delivery_charges")
    private BigDecimal deliveryCharges;

    @Lob
    @Column(name = "address", nullable = false)
    private String address;

    @Column(name = "pincode")
    private String pincode;

    @Lob
    @Column(name = "landmark")
    private String landmark;

    @Column(name = "sku", nullable = false, unique = true)
    private String sku;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "is_accepted", nullable = false)
    private Boolean isAccepted;

    @Column(name = "is_featured", nullable = false)
    private Boolean isFeatured;

    @Column(name = "commission_rate", nullable = false)
    private BigDecimal commissionRate;

    @Column(name = "delivery_type", nullable = false)
    private Integer deliveryType;

    @Column(name = "delivery_radius", nullable = false)
    private BigDecimal deliveryRadius;

    @Column(name = "delivery_charge_type", nullable = false)
    private String deliveryChargeType;

    @Column(name = "base_delivery_charge")
    private BigDecimal baseDeliveryCharge;

    @Column(name = "base_delivery_distance")
    private Integer baseDeliveryDistance;

    @Column(name = "extra_delivery_charge")
    private BigDecimal extraDeliveryCharge;

    @Column(name = "extra_delivery_distance")
    private Integer extraDeliveryDistance;

    @Column(name = "min_order_price", nullable = false)
    private BigDecimal minOrderPrice;

    @Column(name = "is_notifiable")
    private Boolean isNotifiable;

    @Column(name = "auto_acceptable", nullable = false)
    private Boolean autoAcceptable;

    @Lob
    @Column(name = "schedule_data")
    private String scheduleData;

    @Column(name = "is_schedulable", nullable = false)
    private Boolean isSchedulable;

    @Column(name = "is_accept_cod", nullable = false)
    private Boolean isAcceptCod;
}
