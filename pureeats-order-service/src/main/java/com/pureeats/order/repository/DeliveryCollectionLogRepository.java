package com.pureeats.order.repository;

import com.pureeats.domain.entity.DeliveryCollectionLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryCollectionLogRepository extends JpaRepository<DeliveryCollectionLog, Long> {
}
