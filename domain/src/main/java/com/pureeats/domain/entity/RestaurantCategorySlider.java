package com.pureeats.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "restaurant_category_sliders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RestaurantCategorySlider {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Lob
    @Column(name = "image")
    private String image;

    @Lob
    @Column(name = "image_placeholder")
    private String imagePlaceholder;

    @Lob
    @Column(name = "categories_ids", nullable = false)
    private String categoriesIds;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
