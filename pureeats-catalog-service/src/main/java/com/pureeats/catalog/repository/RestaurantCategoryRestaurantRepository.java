package com.pureeats.catalog.repository;

import com.pureeats.domain.entity.RestaurantCategoryRestaurant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RestaurantCategoryRestaurantRepository extends JpaRepository<RestaurantCategoryRestaurant, Long> {
    List<RestaurantCategoryRestaurant> findByRestaurantCategoryId(Long restaurantCategoryId);

    List<RestaurantCategoryRestaurant> findByRestaurantId(Long restaurantId);
}
