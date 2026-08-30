package com.pureeats.order.repository;

import com.pureeats.domain.entity.GpsTable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GpsTableRepository extends JpaRepository<GpsTable, Long> {
    Optional<GpsTable> findFirstByOrderIdOrderByUpdatedAtDesc(Integer orderId);
}
