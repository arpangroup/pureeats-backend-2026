package com.pureeats.catalog.repository;

import com.pureeats.domain.entity.Slide;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SlideRepository extends JpaRepository<Slide, Long> {
    List<Slide> findByPromoSliderIdAndIsActiveTrue(Integer promoSliderId);
}
