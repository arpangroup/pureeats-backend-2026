package com.pureeats.catalog.repository;

import com.pureeats.domain.entity.PopularGeoPlace;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PopularGeoPlaceRepository extends JpaRepository<PopularGeoPlace, Long> {
    List<PopularGeoPlace> findByIsActiveTrue();
}
