package com.pureeats.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "supports")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Support {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Column(name = "order_id")
    private Integer orderId;

    @Column(name = "resturant_id")
    private Integer resturantId;

    @Lob
    @Column(name = "issue")
    private String issue;

    @Column(name = "message")
    private String message;

    @Column(name = "media")
    private String media;

    @Column(name = "resolved", nullable = false)
    private Integer resolved;

    @Column(name = "resolved_by")
    private Integer resolvedBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
