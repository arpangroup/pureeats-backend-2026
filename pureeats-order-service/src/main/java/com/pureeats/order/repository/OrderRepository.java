package com.pureeats.order.repository;

import com.pureeats.domain.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByUserIdOrderByCreatedAtDesc(Integer userId);

    List<Order> findByRestaurantIdAndOrderstatusIdOrderByCreatedAtDesc(Integer restaurantId, Integer orderstatusId);

    List<Order> findByOrderstatusIdInOrderByCreatedAtDesc(List<Integer> orderstatusIds);
}
