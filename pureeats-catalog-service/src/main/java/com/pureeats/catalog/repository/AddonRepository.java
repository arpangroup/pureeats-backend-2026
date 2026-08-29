package com.pureeats.catalog.repository;

import com.pureeats.domain.entity.Addon;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AddonRepository extends JpaRepository<Addon, Long> {
    List<Addon> findByAddonCategoryIdAndIsActiveTrue(Integer addonCategoryId);

    List<Addon> findByUserId(Integer userId);

    /** Admin listing - every addon, optionally scoped to one category. */
    @Query("select a from Addon a where :addonCategoryId is null or a.addonCategoryId = :addonCategoryId")
    Page<Addon> findPage(@Param("addonCategoryId") Integer addonCategoryId, Pageable pageable);
}
