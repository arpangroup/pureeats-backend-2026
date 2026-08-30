package com.pureeats.order.repository;

import com.pureeats.domain.entity.OrderItemAddon;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderItemAddonRepository extends JpaRepository<OrderItemAddon, Long> {
    List<OrderItemAddon> findByOrderitemId(Integer orderItemId);
}
