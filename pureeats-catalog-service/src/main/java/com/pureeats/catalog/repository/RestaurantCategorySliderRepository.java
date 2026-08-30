package com.pureeats.catalog.repository;

import com.pureeats.domain.entity.RestaurantCategorySlider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RestaurantCategorySliderRepository extends JpaRepository<RestaurantCategorySlider, Long> {
    List<RestaurantCategorySlider> findByIsActiveTrue();

    @Query("select s from RestaurantCategorySlider s where (:search is null or :search = '' or lower(s.name) like lower(concat('%', :search, '%')))")
    Page<RestaurantCategorySlider> findPage(@Param("search") String search, Pageable pageable);
}
