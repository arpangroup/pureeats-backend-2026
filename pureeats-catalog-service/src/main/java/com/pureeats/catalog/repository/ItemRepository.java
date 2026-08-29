package com.pureeats.catalog.repository;

import com.pureeats.domain.entity.Item;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ItemRepository extends JpaRepository<Item, Long> {
    List<Item> findByRestaurantIdAndIsActiveTrue(Integer restaurantId);

    List<Item> findByRestaurantId(Integer restaurantId);

    List<Item> findByItemCategoryId(Integer itemCategoryId);
}
