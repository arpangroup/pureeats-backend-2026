package com.pureeats.catalog.repository;

import com.pureeats.domain.entity.RestaurantCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RestaurantCategoryRepository extends JpaRepository<RestaurantCategory, Long> {
    List<RestaurantCategory> findByIsActiveTrue();

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}
