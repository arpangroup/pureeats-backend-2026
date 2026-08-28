package com.pureeats.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "payable_type", nullable = false)
    private String payableType;

    @Column(name = "payable_id", nullable = false)
    private Long payableId;

    @Column(name = "wallet_id")
    private Long walletId;

    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "confirmed", nullable = false)
    private Boolean confirmed;

    @Lob
    @Column(name = "meta")
    private String meta;

    @Column(name = "uuid", nullable = false, unique = true)
    private String uuid;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
