package com.pureeats.order.repository;

import com.pureeats.domain.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByUserIdOrderByCreatedAtDesc(Integer userId);

    List<Order> findByRestaurantIdAndOrderstatusIdOrderByCreatedAtDesc(Integer restaurantId, Integer orderstatusId);

    List<Order> findByOrderstatusIdInOrderByCreatedAtDesc(List<Integer> orderstatusIds);

    /** Admin listing - every order across every customer/restaurant, optionally filtered. */
    @Query("select o from Order o where (:restaurantId is null or o.restaurantId = :restaurantId) " +
            "and (:statusId is null or o.orderstatusId = :statusId) " +
            "and (:search is null or :search = '' or lower(o.uniqueOrderId) like lower(concat('%', :search, '%')))")
    Page<Order> findPage(@Param("restaurantId") Integer restaurantId, @Param("statusId") Integer statusId,
                          @Param("search") String search, Pageable pageable);
}
