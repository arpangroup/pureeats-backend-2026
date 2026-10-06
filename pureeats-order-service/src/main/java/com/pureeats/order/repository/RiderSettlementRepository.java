package com.pureeats.order.repository;

import com.pureeats.domain.entity.RiderSettlement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RiderSettlementRepository extends JpaRepository<RiderSettlement, Long> {
    List<RiderSettlement> findByRiderUserIdOrderByCreatedAtDesc(Long riderUserId);
}
