package com.pureeats.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "transfers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Transfer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "from_type", nullable = false)
    private String fromType;

    @Column(name = "from_id", nullable = false)
    private Long fromId;

    @Column(name = "to_type", nullable = false)
    private String toType;

    @Column(name = "to_id", nullable = false)
    private Long toId;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "status_last")
    private String statusLast;

    @Column(name = "deposit_id", nullable = false)
    private Long depositId;

    @Column(name = "withdraw_id", nullable = false)
    private Long withdrawId;

    @Column(name = "discount", nullable = false)
    private Long discount;

    @Column(name = "fee", nullable = false)
    private Long fee;

    @Column(name = "uuid", nullable = false, unique = true)
    private String uuid;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
