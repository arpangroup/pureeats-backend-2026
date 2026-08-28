package com.pureeats.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "restaurant_earnings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RestaurantEarning {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "restaurant_id", nullable = false)
    private Integer restaurantId;

    @Column(name = "user_id")
    private Integer userId;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "is_requested", nullable = false)
    private Boolean isRequested;

    @Column(name = "is_processed", nullable = false)
    private Boolean isProcessed;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "restaurant_payout_id")
    private Integer restaurantPayoutId;
}
