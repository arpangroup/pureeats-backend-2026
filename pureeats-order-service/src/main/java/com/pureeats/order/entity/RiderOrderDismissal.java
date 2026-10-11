package com.pureeats.order.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A delivery partner dismissed a new-order alert: the order is hidden from their available orders until it's
 * offered again (RiderOfferService re-offers unassigned orders, which clears these rows for that order).
 */
@Entity
@Table(name = "rider_order_dismissals", indexes = {
        @Index(name = "idx_rider_order_dismissals_rider", columnList = "rider_user_id"),
        @Index(name = "idx_rider_order_dismissals_order", columnList = "order_id")
}, uniqueConstraints = @UniqueConstraint(name = "uk_rider_order_dismissal", columnNames = {"order_id", "rider_user_id"}))
@Getter
@Setter
@NoArgsConstructor
public class RiderOrderDismissal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "rider_user_id", nullable = false)
    private Long riderUserId;

    @Column(name = "dismissed_at", nullable = false)
    private LocalDateTime dismissedAt;
}
