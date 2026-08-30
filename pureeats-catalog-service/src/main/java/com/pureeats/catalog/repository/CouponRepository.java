package com.pureeats.catalog.repository;

import com.pureeats.domain.entity.Coupon;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CouponRepository extends JpaRepository<Coupon, Long> {
    Optional<Coupon> findByCodeIgnoreCaseAndIsActiveTrue(String code);

    Optional<Coupon> findByCodeIgnoreCase(String code);

    List<Coupon> findByIsActiveTrueAndRestaurantIdIn(List<Integer> restaurantIdsIncludingZeroForGlobal);

    /** Admin listing - every coupon, optionally filtered by a name/code search. */
    @Query("select c from Coupon c where :search is null or :search = '' " +
            "or lower(c.name) like lower(concat('%', :search, '%')) or lower(c.code) like lower(concat('%', :search, '%'))")
    Page<Coupon> findPage(@Param("search") String search, Pageable pageable);

    /** Store-owner listing - only coupons scoped to this one restaurant (not the global ones admins manage). */
    @Query("select c from Coupon c where c.restaurantId = :restaurantId and (:search is null or :search = '' " +
            "or lower(c.name) like lower(concat('%', :search, '%')) or lower(c.code) like lower(concat('%', :search, '%')))")
    Page<Coupon> findByRestaurantIdPage(@Param("restaurantId") Integer restaurantId, @Param("search") String search, Pageable pageable);
}
