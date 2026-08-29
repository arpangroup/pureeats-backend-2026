package com.pureeats.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Item {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "restaurant_id", nullable = false)
    private Integer restaurantId;

    @Column(name = "item_category_id", nullable = false)
    private Integer itemCategoryId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "price", nullable = false)
    private BigDecimal price;

    @Column(name = "old_price", nullable = false)
    private BigDecimal oldPrice;

    @Column(name = "image")
    private String image;

    @Column(name = "is_recommended", nullable = false)
    private Boolean isRecommended;

    @Column(name = "is_popular", nullable = false)
    private Boolean isPopular;

    @Column(name = "is_new", nullable = false)
    private Boolean isNew;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** Backtick-quoted: "desc" is a reserved word in MySQL/MariaDB and breaks unquoted DDL/DML. */
    @Lob
    @Column(name = "`desc`")
    private String desc;

    @Column(name = "placeholder_image")
    private String placeholderImage;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "is_veg")
    private Boolean isVeg;
}
