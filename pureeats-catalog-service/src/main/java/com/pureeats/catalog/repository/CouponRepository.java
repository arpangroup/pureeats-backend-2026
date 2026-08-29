package com.pureeats.catalog.repository;

import com.pureeats.domain.entity.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CouponRepository extends JpaRepository<Coupon, Long> {
    Optional<Coupon> findByCodeIgnoreCaseAndIsActiveTrue(String code);

    List<Coupon> findByIsActiveTrueAndRestaurantIdIn(List<Integer> restaurantIdsIncludingZeroForGlobal);
}
