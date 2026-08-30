package com.pureeats.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "order_item_addons")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemAddon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "orderitem_id", nullable = false)
    private Integer orderitemId;

    @Column(name = "addon_category_name", nullable = false)
    private String addonCategoryName;

    @Column(name = "addon_name", nullable = false)
    private String addonName;

    @Column(name = "addon_price", nullable = false)
    private BigDecimal addonPrice;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
