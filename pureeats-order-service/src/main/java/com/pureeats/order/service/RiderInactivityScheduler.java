package com.pureeats.order.service;

import com.pureeats.catalog.service.SettingSchemaService;
import com.pureeats.catalog.service.SettingValueService;
import com.pureeats.domain.entity.DeliveryGuyDetail;
import com.pureeats.domain.entity.User;
import com.pureeats.user.repository.DeliveryGuyDetailRepository;
import com.pureeats.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * "Forced stop": flips an online rider to offline once their app has gone quiet - no location ping
 * ({@code lastSeenAt}) for longer than Settings -> Delivery Application -> Inactivity auto-offline ->
 * timeout (default 10 min). Recorded with {@link DeliveryGuyDetail#OFFLINE_REASON_INACTIVITY} so the
 * admin panel can tell it apart from a rider going offline themself, and so the rider app can explain
 * it when it comes back to the foreground.
 * <p>
 * Riders with a delivery still in progress are skipped: their app being backgrounded (e.g. while
 * navigating in Google Maps) is expected, and offlining them mid-delivery helps no one.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RiderInactivityScheduler {

    private final DeliveryGuyDetailRepository deliveryGuyDetailRepository;
    private final UserRepository userRepository;
    private final DeliveryOrderService deliveryOrderService;
    private final SettingValueService settingValueService;

    @Scheduled(fixedDelayString = "${pureeats.delivery.inactivity-check-interval-ms:60000}",
            initialDelayString = "${pureeats.delivery.inactivity-check-interval-ms:60000}")
    @Transactional
    public void sweep() {
        if (!settingValueService.getBoolean(SettingSchemaService.DRIVER_AUTO_OFFLINE_ENABLED, true)) {
            return;
        }
        int timeoutMinutes = settingValueService.getInt(SettingSchemaService.DRIVER_INACTIVITY_TIMEOUT_MINUTES, 10);
        if (timeoutMinutes <= 0) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff = now.minusMinutes(timeoutMinutes);
        List<DeliveryGuyDetail> online = deliveryGuyDetailRepository.findByIsOnlineTrue();
        int forced = 0;
        for (DeliveryGuyDetail rider : online) {
            LocalDateTime lastSeen = rider.getLastSeenAt() != null ? rider.getLastSeenAt() : rider.getStatusChangedAt();
            if (lastSeen != null && lastSeen.isAfter(cutoff)) {
                continue;
            }
            if (hasDeliveryInProgress(rider)) {
                continue;
            }
            rider.setIsOnline(false);
            rider.setOfflineReason(DeliveryGuyDetail.OFFLINE_REASON_INACTIVITY);
            rider.setStatusChangedAt(now);
            deliveryGuyDetailRepository.save(rider);
            forced++;
            log.info("Rider profile {} ({}) auto-set OFFLINE - no location ping since {} (timeout {} min)",
                    rider.getId(), rider.getName(), lastSeen, timeoutMinutes);
        }
        if (forced > 0) {
            log.info("Inactivity sweep forced {} of {} online rider(s) offline", forced, online.size());
        }
    }

    private boolean hasDeliveryInProgress(DeliveryGuyDetail rider) {
        return userRepository.findByDeliveryGuyDetailId(rider.getId().intValue())
                .map(User::getId)
                .map(userId -> deliveryOrderService.countDeliveriesInProgress(userId) > 0)
                .orElse(false);
    }
}
