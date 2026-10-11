package com.pureeats.order.repository;

import com.pureeats.order.entity.RiderOrderDismissal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RiderOrderDismissalRepository extends JpaRepository<RiderOrderDismissal, Long> {

    Optional<RiderOrderDismissal> findByOrderIdAndRiderUserId(Long orderId, Long riderUserId);

    List<RiderOrderDismissal> findByRiderUserId(Long riderUserId);

    List<RiderOrderDismissal> findByOrderIdOrderByDismissedAtAsc(Long orderId);
}
