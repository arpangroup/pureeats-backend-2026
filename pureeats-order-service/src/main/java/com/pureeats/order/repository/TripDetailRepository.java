package com.pureeats.order.repository;

import com.pureeats.domain.entity.TripDetail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TripDetailRepository extends JpaRepository<TripDetail, Long> {
    Optional<TripDetail> findByOrderId(Integer orderId);

    List<TripDetail> findByRiderId(Integer riderId);

    // See OrderRepository's reports section for why these are plain derived methods, not one
    // "(:from is null or ...)" @Query - Postgres can't type-infer a param used only in IS NULL.
    List<TripDetail> findByCreatedAtLessThanEqual(LocalDateTime to);

    List<TripDetail> findByCreatedAtBetween(LocalDateTime from, LocalDateTime to);
}
