package com.pureeats.order.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * One delivery partner alerted about one offer of an order (round 1 = the first alert, higher = re-offers) - so the
 * admin's order page can show who was told, and when.
 */
@Entity
@Table(name = "rider_order_offers", indexes = @Index(name = "idx_rider_order_offers_order", columnList = "order_id"))
@Getter
@Setter
@NoArgsConstructor
public class RiderOrderOffer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "offer_round", nullable = false)
    private Integer offerRound;

    @Column(name = "rider_user_id", nullable = false)
    private Long riderUserId;

    @Column(name = "notified_at", nullable = false)
    private LocalDateTime notifiedAt;
}
