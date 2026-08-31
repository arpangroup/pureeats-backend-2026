package com.pureeats.user.security.device;

import com.pureeats.user.entity.UserDevice;
import com.pureeats.user.repository.UserDeviceRepository;
import com.pureeats.user.security.metadata.RequestMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/** Upserts the {@code user_devices} row for a (userId, deviceId) pair seen on a successful login. */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceService {

    private final UserDeviceRepository userDeviceRepository;

    @Transactional
    public void recordLogin(Long userId, RequestMetadata metadata) {
        LocalDateTime now = LocalDateTime.now();
        Optional<UserDevice> existing = userDeviceRepository.findByUserIdAndDeviceId(userId, metadata.deviceId());
        if (existing.isEmpty()) {
            log.info("New device seen for user {}", userId);
        } else {
            log.debug("Known device login for user {}", userId);
        }
        UserDevice device = existing.orElseGet(() -> {
            UserDevice created = new UserDevice();
            created.setUserId(userId);
            created.setDeviceId(metadata.deviceId());
            created.setFirstSeenAt(now);
            return created;
        });
        device.setDeviceType(metadata.deviceType());
        device.setBrowser(metadata.browser());
        device.setBrowserVersion(metadata.browserVersion());
        device.setOperatingSystem(metadata.operatingSystem());
        device.setOsVersion(metadata.osVersion());
        device.setUserAgent(metadata.userAgent());
        device.setIpAddress(metadata.ipAddress());
        device.setLastSeenAt(now);
        device.setLastLoginAt(now);
        userDeviceRepository.save(device);
    }
}
