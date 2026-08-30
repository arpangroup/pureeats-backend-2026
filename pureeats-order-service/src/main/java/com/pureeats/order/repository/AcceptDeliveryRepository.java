package com.pureeats.order.repository;

import com.pureeats.domain.entity.AcceptDelivery;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AcceptDeliveryRepository extends JpaRepository<AcceptDelivery, Long> {
    Optional<AcceptDelivery> findByOrderId(Integer orderId);

    java.util.List<AcceptDelivery> findByUserIdAndIsCompleteFalse(Integer userId);

    java.util.List<AcceptDelivery> findByIsCompleteFalse();
}
