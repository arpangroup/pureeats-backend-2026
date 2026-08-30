package com.pureeats.catalog.repository;

import com.pureeats.domain.entity.Location;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LocationRepository extends JpaRepository<Location, Long> {
    List<Location> findByIsActiveTrue();

    List<Location> findByIsActiveTrueAndIsPopularTrue();

    List<Location> findByIsActiveTrueAndNameContainingIgnoreCase(String query);
}
