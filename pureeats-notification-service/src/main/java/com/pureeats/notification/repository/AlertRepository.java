package com.pureeats.notification.repository;

import com.pureeats.domain.entity.Alert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface AlertRepository extends JpaRepository<Alert, Long> {
    List<Alert> findByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(Long userId, LocalDateTime after);

    List<Alert> findByUserIdAndIsReadFalse(Long userId);
}
