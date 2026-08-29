package com.pureeats.catalog.repository;

import com.pureeats.domain.entity.Translation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TranslationRepository extends JpaRepository<Translation, Long> {
    List<Translation> findByIsActiveTrue();
}
