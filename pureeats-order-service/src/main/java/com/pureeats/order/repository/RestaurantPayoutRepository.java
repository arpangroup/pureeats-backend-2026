package com.pureeats.order.repository;

import com.pureeats.domain.entity.RestaurantPayout;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RestaurantPayoutRepository extends JpaRepository<RestaurantPayout, Long> {
}
