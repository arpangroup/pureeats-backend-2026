package com.pureeats.catalog.repository;

import com.pureeats.domain.entity.RestaurantCategorySlider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RestaurantCategorySliderRepository extends JpaRepository<RestaurantCategorySlider, Long> {
    List<RestaurantCategorySlider> findByIsActiveTrue();
}
