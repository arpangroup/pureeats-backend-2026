package com.pureeats.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "ratings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Rating {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "rating", nullable = false)
    private Integer rating;

    @Column(name = "order_id", nullable = false)
    private Integer orderId;

    @Column(name = "name")
    private String name;

    @Column(name = "tags")
    private String tags;

    @Lob
    @Column(name = "comment")
    private String comment;

    @Column(name = "rateable_type", nullable = false)
    private String rateableType;

    @Column(name = "rateable_id", nullable = false)
    private Long rateableId;

    @Column(name = "user_id", nullable = false)
    private Long userId;
}
