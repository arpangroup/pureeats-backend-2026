package com.pureeats.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "restaurant_category_restaurant")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RestaurantCategoryRestaurant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "restaurant_category_id", nullable = false)
    private Long restaurantCategoryId;

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
