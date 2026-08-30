package com.pureeats.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "slides")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Slide {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    /** Exactly one of promoSliderId / restaurantCategorySliderId is set, depending on which container this slide belongs to. */
    @Column(name = "promo_slider_id")
    private Integer promoSliderId;

    @Column(name = "restaurant_category_slider_id")
    private Integer restaurantCategorySliderId;

    @Column(name = "unique_id")
    private String uniqueId;

    @Column(name = "name")
    private String name;

    @Column(name = "description")
    private String description;

    @Lob
    @Column(name = "image")
    private String image;

    @Lob
    @Column(name = "image_placeholder")
    private String imagePlaceholder;

    @Column(name = "link_type")
    private String linkType;

    @Column(name = "category_id")
    private Integer categoryId;

    @Column(name = "restaurant_id")
    private Integer restaurantId;

    @Lob
    @Column(name = "url")
    private String url;

    @Column(name = "position_id")
    private Integer positionId;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
