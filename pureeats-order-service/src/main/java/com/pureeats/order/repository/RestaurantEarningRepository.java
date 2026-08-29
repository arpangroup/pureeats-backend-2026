package com.pureeats.order.repository;

import com.pureeats.domain.entity.RestaurantEarning;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RestaurantEarningRepository extends JpaRepository<RestaurantEarning, Long> {
    List<RestaurantEarning> findByRestaurantIdAndIsProcessedFalse(Integer restaurantId);
}
