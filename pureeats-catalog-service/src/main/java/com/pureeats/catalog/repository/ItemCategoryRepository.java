package com.pureeats.catalog.repository;

import com.pureeats.domain.entity.ItemCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ItemCategoryRepository extends JpaRepository<ItemCategory, Long> {
    List<ItemCategory> findByUserId(Integer userId);
}
