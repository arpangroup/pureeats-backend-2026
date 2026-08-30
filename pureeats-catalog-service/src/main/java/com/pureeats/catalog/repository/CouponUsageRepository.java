package com.pureeats.catalog.repository;

import com.pureeats.domain.entity.CouponUsage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CouponUsageRepository extends JpaRepository<CouponUsage, Long> {
    List<CouponUsage> findByCouponIdAndUserId(Integer couponId, Integer userId);

    List<CouponUsage> findByCouponIdOrderByCreatedAtDesc(Integer couponId);
}
