package com.pureeats.order.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A delivery partner dismissed a new-order alert: the order is hidden from their available orders until it's
 * offered again in a later round (RiderOfferService). Kept afterwards, so the admin sees who dismissed and when.
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

    /** The offer round dismissed - the order shows again once it's offered in a later round. */
    @Column(name = "offer_round")
    private Integer offerRound;
}
