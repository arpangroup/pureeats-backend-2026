package com.pureeats.catalog.repository;

import com.pureeats.domain.entity.PromoSlider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PromoSliderRepository extends JpaRepository<PromoSlider, Long> {
    List<PromoSlider> findByIsActiveTrue();
}
