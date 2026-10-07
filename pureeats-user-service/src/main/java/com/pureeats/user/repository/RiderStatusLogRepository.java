package com.pureeats.user.repository;

import com.pureeats.domain.entity.RiderStatusLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RiderStatusLogRepository extends JpaRepository<RiderStatusLog, Long> {
    List<RiderStatusLog> findByRiderUserIdOrderByCreatedAtDesc(Long riderUserId, Pageable pageable);
}
