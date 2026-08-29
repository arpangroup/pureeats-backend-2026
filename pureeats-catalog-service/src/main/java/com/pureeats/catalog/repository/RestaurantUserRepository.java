package com.pureeats.catalog.repository;

import com.pureeats.domain.entity.RestaurantUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RestaurantUserRepository extends JpaRepository<RestaurantUser, Long> {
    List<RestaurantUser> findByUserId(Long userId);

    List<RestaurantUser> findByRestaurantId(Long restaurantId);

    Optional<RestaurantUser> findByUserIdAndRestaurantId(Long userId, Long restaurantId);
}
