package com.pureeats.catalog.repository;

import com.pureeats.domain.entity.AddonCategoryItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AddonCategoryItemRepository extends JpaRepository<AddonCategoryItem, Long> {
    List<AddonCategoryItem> findByItemId(Long itemId);
}
