package com.pureeats.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Restricts which restaurants a delivery partner may pick up orders from - admin-managed allowlist. */
@Entity
@Table(name = "delivery_guy_restaurants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryGuyRestaurant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "delivery_guy_detail_id", nullable = false)
    private Long deliveryGuyDetailId;

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
