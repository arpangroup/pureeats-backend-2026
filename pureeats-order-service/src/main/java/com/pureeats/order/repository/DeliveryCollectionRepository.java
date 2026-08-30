package com.pureeats.order.repository;

import com.pureeats.domain.entity.DeliveryCollection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DeliveryCollectionRepository extends JpaRepository<DeliveryCollection, Long> {
    Optional<DeliveryCollection> findByUserId(Integer userId);
}
