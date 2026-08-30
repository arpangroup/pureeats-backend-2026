package com.pureeats.order.repository;

import com.pureeats.domain.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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

    // ---- Dashboard aggregates ----

    @Query("select coalesce(sum(o.total), 0) from Order o")
    BigDecimal sumTotal();

    @Query("select coalesce(sum(o.total), 0) from Order o where o.restaurantId = :restaurantId")
    BigDecimal sumTotalForRestaurant(@Param("restaurantId") Integer restaurantId);

    long countByRestaurantId(Integer restaurantId);

    List<Order> findByRestaurantId(Integer restaurantId);

    @Query("select o.orderstatusId as statusId, count(o) as cnt from Order o group by o.orderstatusId")
    List<OrderStatusCountProjection> countGroupedByStatus();

    @Query("select o.restaurantId as restaurantId, sum(o.total) as revenue, count(o) as cnt " +
            "from Order o where o.restaurantId is not null group by o.restaurantId order by sum(o.total) desc")
    List<RestaurantRevenueProjection> revenueByRestaurant(Pageable pageable);

    List<Order> findByCreatedAtGreaterThanEqual(LocalDateTime from);

    List<Order> findByRestaurantIdAndCreatedAtGreaterThanEqual(Integer restaurantId, LocalDateTime from);

    interface OrderStatusCountProjection {
        Integer getStatusId();
        Long getCnt();
    }

    interface RestaurantRevenueProjection {
        Integer getRestaurantId();
        BigDecimal getRevenue();
        Long getCnt();
    }
}
