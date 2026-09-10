package com.pureeats.catalog.repository;

import com.pureeats.catalog.entity.SettingHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettingHistoryRepository extends JpaRepository<SettingHistory, Long> {
    Page<SettingHistory> findAllByOrderByUpdatedAtDesc(Pageable pageable);

    Page<SettingHistory> findByFieldKeyOrderByUpdatedAtDesc(String fieldKey, Pageable pageable);

    Page<SettingHistory> findBySourceOrderByUpdatedAtDesc(String source, Pageable pageable);
}
