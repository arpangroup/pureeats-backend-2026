package com.pureeats.user.security.device;

import com.pureeats.user.entity.LoginHistory;
import com.pureeats.user.enums.LoginMethod;
import com.pureeats.user.repository.LoginHistoryRepository;
import com.pureeats.user.security.geolocation.IpGeolocationService;
import com.pureeats.user.security.metadata.RequestMetadata;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** Append-only login attempt log, kept separate from {@code user_sessions} (live) and {@code audit_logs} (all events). */
@Service
@RequiredArgsConstructor
public class LoginHistoryRecorder {

    private final LoginHistoryRepository loginHistoryRepository;
    private final IpGeolocationService ipGeolocationService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long userId, LoginMethod method, boolean success, RequestMetadata metadata, String failureReason) {
        LoginHistory history = new LoginHistory();
        history.setUserId(userId);
        history.setLoginMethod(method);
        history.setStatus(success ? "SUCCESS" : "FAILED");
        history.setIpAddress(metadata.ipAddress());
        history.setDeviceId(metadata.deviceId());
        history.setUserAgent(metadata.userAgent());
        history.setOccurredAt(LocalDateTime.now());
        history.setFailureReason(failureReason);

        ipGeolocationService.resolve(metadata.ipAddress()).ifPresent(geo -> {
            history.setCountry(geo.country());
            history.setRegion(geo.region());
            history.setCity(geo.city());
            history.setLatitude(geo.latitude());
            history.setLongitude(geo.longitude());
        });

        loginHistoryRepository.save(history);
    }
}
