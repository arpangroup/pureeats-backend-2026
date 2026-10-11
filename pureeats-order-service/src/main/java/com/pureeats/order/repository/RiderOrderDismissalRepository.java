package com.pureeats.order.repository;

import com.pureeats.order.entity.RiderOrderDismissal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RiderOrderDismissalRepository extends JpaRepository<RiderOrderDismissal, Long> {

    boolean existsByOrderIdAndRiderUserId(Long orderId, Long riderUserId);

    @Query("select d.orderId from RiderOrderDismissal d where d.riderUserId = :riderUserId")
    List<Long> findOrderIdsByRiderUserId(@Param("riderUserId") Long riderUserId);

    @Modifying
    @Query("delete from RiderOrderDismissal d where d.orderId = :orderId")
    int deleteByOrderId(@Param("orderId") Long orderId);
}
