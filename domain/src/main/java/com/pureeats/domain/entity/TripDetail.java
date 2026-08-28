package com.pureeats.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "trip_details")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TripDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Integer orderId;

    @Column(name = "customer_id", nullable = false)
    private Integer customerId;

    @Column(name = "restaurant_id", nullable = false)
    private Integer restaurantId;

    @Column(name = "rider_id", nullable = false)
    private Integer riderId;

    @Column(name = "delivery_collection_id", nullable = false)
    private Integer deliveryCollectionId;

    @Column(name = "distance_travelled", nullable = false)
    private BigDecimal distanceTravelled;

    @Column(name = "rider_earning", nullable = false)
    private BigDecimal riderEarning;

    @Column(name = "restaurant_earning", nullable = false)
    private BigDecimal restaurantEarning;

    @Column(name = "cash_collected_from_customer", nullable = false)
    private BigDecimal cashCollectedFromCustomer;

    @Column(name = "cash_on_hold", nullable = false)
    private BigDecimal cashOnHold;

    @Column(name = "route")
    private String route;

    @Lob
    @Column(name = "meta")
    private String meta;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "is_settlement_done", nullable = false)
    private Integer isSettlementDone;
}
