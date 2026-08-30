package com.pureeats.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "gps_tables")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GpsTable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Integer orderId;

    @Column(name = "user_lat")
    private String userLat;

    @Column(name = "user_long")
    private String userLong;

    @Column(name = "delivery_lat")
    private String deliveryLat;

    @Column(name = "delivery_long")
    private String deliveryLong;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "heading")
    private String heading;

    @Column(name = "bearing")
    private String bearing;
}
