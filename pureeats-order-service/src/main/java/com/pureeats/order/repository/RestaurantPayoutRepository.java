package com.pureeats.order.repository;

import com.pureeats.domain.entity.RestaurantPayout;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RestaurantPayoutRepository extends JpaRepository<RestaurantPayout, Long> {
    Page<RestaurantPayout> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
