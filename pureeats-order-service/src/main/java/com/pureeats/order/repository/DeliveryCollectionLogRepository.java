package com.pureeats.order.repository;

import com.pureeats.domain.entity.DeliveryCollectionLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeliveryCollectionLogRepository extends JpaRepository<DeliveryCollectionLog, Long> {
    List<DeliveryCollectionLog> findByDeliveryCollectionIdOrderByCreatedAtDesc(Integer deliveryCollectionId);
}
