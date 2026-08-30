package com.pureeats.domain.entity;

import com.pureeats.domain.common.audit.AuditableEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "accept_deliveries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AcceptDelivery extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Integer orderId;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Column(name = "customer_id", nullable = false)
    private Integer customerId;

    @Column(name = "is_complete", nullable = false)
    private Boolean isComplete;
}
