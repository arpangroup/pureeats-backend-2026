package com.pureeats.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "delivery_collection_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryCollectionLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "delivery_collection_id", nullable = false)
    private Integer deliveryCollectionId;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "type")
    private String type;

    @Lob
    @Column(name = "message")
    private String message;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
