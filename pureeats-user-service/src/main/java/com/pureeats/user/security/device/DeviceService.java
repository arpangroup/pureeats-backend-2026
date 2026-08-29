package com.pureeats.user.security.device;

import com.pureeats.user.entity.UserDevice;
import com.pureeats.user.repository.UserDeviceRepository;
import com.pureeats.user.security.metadata.RequestMetadata;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** Upserts the {@code user_devices} row for a (userId, deviceId) pair seen on a successful login. */
@Service
@RequiredArgsConstructor
public class DeviceService {

    private final UserDeviceRepository userDeviceRepository;

    @Transactional
    public void recordLogin(Long userId, RequestMetadata metadata) {
        LocalDateTime now = LocalDateTime.now();
        UserDevice device = userDeviceRepository.findByUserIdAndDeviceId(userId, metadata.deviceId())
                .orElseGet(() -> {
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
