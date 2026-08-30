package com.pureeats.order.repository;

import com.pureeats.domain.entity.TripDetail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TripDetailRepository extends JpaRepository<TripDetail, Long> {
    Optional<TripDetail> findByOrderId(Integer orderId);

    List<TripDetail> findByRiderId(Integer riderId);
}
