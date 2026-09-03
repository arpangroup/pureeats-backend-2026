package com.pureeats.order.repository;

import com.pureeats.domain.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    List<OrderItem> findByOrderId(Integer orderId);

    List<OrderItem> findByOrderIdIn(List<Integer> orderIds);
}
