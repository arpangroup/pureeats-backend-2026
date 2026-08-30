package com.pureeats.catalog.repository;

import com.pureeats.catalog.entity.RestaurantAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RestaurantAuditLogRepository extends JpaRepository<RestaurantAuditLog, Long> {
    Page<RestaurantAuditLog> findByRestaurantIdOrderByUpdatedAtDesc(Long restaurantId, Pageable pageable);
}
