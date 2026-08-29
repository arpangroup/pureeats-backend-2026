package com.pureeats.catalog.repository;

import com.pureeats.domain.entity.Restaurant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RestaurantRepository extends JpaRepository<Restaurant, Long> {
    Optional<Restaurant> findBySlug(String slug);

    boolean existsBySku(String sku);

    List<Restaurant> findByIsActiveTrueAndIsAcceptedTrue();

    @Query("select r from Restaurant r where r.isActive = true and r.isAccepted = true " +
            "and lower(r.name) like lower(concat('%', :query, '%'))")
    List<Restaurant> search(@Param("query") String query);

    /** Admin listing - every restaurant, not just active+accepted. */
    @Query("select r from Restaurant r where :search is null or :search = '' " +
            "or lower(r.name) like lower(concat('%', :search, '%'))")
    Page<Restaurant> findPage(@Param("search") String search, Pageable pageable);
}
