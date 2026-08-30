package com.pureeats.order.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * One row per order status transition - the order's full journey, and "who did it" (a store
 * owner accepting, a rider delivering, a customer cancelling, or an admin override).
 */
@Entity
@Table(name = "order_status_logs", indexes = {
        @Index(name = "idx_order_status_logs_order", columnList = "order_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderStatusLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "from_status", length = 32)
    private String fromStatus;

    @Column(name = "to_status", nullable = false, length = 32)
    private String toStatus;

    /** ADMIN, STORE_OWNER, DELIVERY, or CUSTOMER. */
    @Column(name = "actor_type", nullable = false, length = 16)
    private String actorType;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "note")
    private String note;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
