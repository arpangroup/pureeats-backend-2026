package com.pureeats.catalog.repository;

import com.pureeats.domain.entity.Item;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ItemRepository extends JpaRepository<Item, Long> {
    List<Item> findByRestaurantIdAndIsActiveTrue(Integer restaurantId);

    List<Item> findByRestaurantId(Integer restaurantId);

    List<Item> findByItemCategoryId(Integer itemCategoryId);

    /** Admin listing - every item, optionally scoped to one restaurant and/or a name search. */
    @Query("select i from Item i where (:restaurantId is null or i.restaurantId = :restaurantId) " +
            "and (:search is null or :search = '' or lower(i.name) like lower(concat('%', :search, '%')))")
    Page<Item> findPage(@Param("restaurantId") Integer restaurantId, @Param("search") String search, Pageable pageable);
}
