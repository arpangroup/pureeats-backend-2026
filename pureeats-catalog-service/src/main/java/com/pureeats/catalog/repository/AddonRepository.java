package com.pureeats.catalog.repository;

import com.pureeats.domain.entity.Addon;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AddonRepository extends JpaRepository<Addon, Long> {
    List<Addon> findByAddonCategoryIdAndIsActiveTrue(Integer addonCategoryId);

    List<Addon> findByUserId(Integer userId);
}
