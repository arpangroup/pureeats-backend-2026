package com.pureeats.catalog.repository;

import com.pureeats.domain.entity.PromoSlider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PromoSliderRepository extends JpaRepository<PromoSlider, Long> {
    List<PromoSlider> findByIsActiveTrue();

    @Query("select p from PromoSlider p where (:search is null or :search = '' or lower(p.name) like lower(concat('%', :search, '%')))")
    Page<PromoSlider> findPage(@Param("search") String search, Pageable pageable);
}
