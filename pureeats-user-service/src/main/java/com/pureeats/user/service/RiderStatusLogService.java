package com.pureeats.user.service;

import com.pureeats.domain.entity.RiderStatusLog;
import com.pureeats.user.repository.RiderStatusLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** Records every online/offline change (rider, inactivity scheduler or admin) so the rider app can show the history. */
@Service
@RequiredArgsConstructor
public class RiderStatusLogService {

    private final RiderStatusLogRepository repository;

    @Transactional
    public void record(Long riderUserId, boolean online, String reason) {
        if (riderUserId == null) return;
        RiderStatusLog log = new RiderStatusLog();
        log.setRiderUserId(riderUserId);
        log.setIsOnline(online);
        log.setReason(reason);
        log.setCreatedAt(LocalDateTime.now());
        repository.save(log);
    }

    @Transactional(readOnly = true)
    public List<RiderStatusLog> recent(Long riderUserId, int limit) {
        return repository.findByRiderUserIdOrderByCreatedAtDesc(riderUserId, PageRequest.of(0, limit));
    }
}
