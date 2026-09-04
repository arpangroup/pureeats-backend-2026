package com.pureeats.order.service;

import com.pureeats.domain.entity.User;
import com.pureeats.notification.dto.NotificationRequest;
import com.pureeats.notification.enums.NotificationChannel;
import com.pureeats.notification.enums.NotificationRecipientRole;
import com.pureeats.notification.enums.NotificationType;
import com.pureeats.notification.service.NotificationRoutingService;
import com.pureeats.notification.service.NotificationService;
import com.pureeats.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;

/**
 * The single entry point every order-status transition (accept/reject/pickup/deliver/cancel/admin
 * override/...) calls to notify whoever needs to know - replaces the old direct
 * {@code notificationDispatchService.notifyUser(...)} calls that hardcoded "always an in-app alert
 * + best-effort push" for every single case. WHO gets notified for a given transition is still the
 * calling business logic's call (an "order accepted" event is inherently about the customer, a
 * "new order" event is inherently about the restaurant owner - that's order-domain knowledge, not
 * something to make configurable) - but WHICH channel(s) that recipient hears it on is fully
 * admin-configurable via {@link NotificationRoutingService}, with zero code change to add e.g.
 * WhatsApp for delivery partners later.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderNotificationService {

    private final NotificationRoutingService notificationRoutingService;
    private final NotificationService notificationService;
    private final UserRepository userRepository;

    /**
     * Dispatches every configured channel via {@link NotificationService#sendAsync} - the caller
     * (an order accept/cancel/assign/deliver action) gets its HTTP response as soon as the order
     * itself is updated, without waiting on an SMTP/SMS/push round-trip. Each channel's outcome is
     * still logged, just from whichever background thread it completes on rather than this one.
     */
    @Transactional(readOnly = true)
    public void notify(NotificationRecipientRole role, Long userId, String title, String body) {
        Set<NotificationChannel> channels = notificationRoutingService.orderStatusChannelsFor(role);
        if (channels.isEmpty()) {
            log.debug("No channels configured for role {} - skipping order notification '{}' to user {}", role, title, userId);
            return;
        }
        Map<String, Object> params = Map.of("title", title, "body", body, "category", "ORDER_UPDATE");
        User user = channels.stream().anyMatch(this::needsExternalDestination)
                ? userRepository.findById(userId).orElse(null) : null;

        for (NotificationChannel channel : channels) {
            String destination = destinationFor(channel, user);
            notificationService.sendAsync(new NotificationRequest(NotificationType.ORDER_STATUS_UPDATE, channel, destination, userId, params))
                    .whenComplete((result, error) -> {
                        if (error != null) {
                            log.warn("Order-status notification on channel {} to user {} threw: {}", channel, userId, error.getMessage());
                        } else if (!result.success()) {
                            log.debug("Order-status notification on channel {} to user {} did not succeed: {}", channel, userId, result.failureReason());
                        }
                    });
        }
    }

    private boolean needsExternalDestination(NotificationChannel channel) {
        return channel == NotificationChannel.EMAIL || channel == NotificationChannel.SMS || channel == NotificationChannel.WHATSAPP;
    }

    /** EMAIL wants an email address, SMS/WHATSAPP want a phone number; PUSH/IN_APP/CONSOLE resolve entirely from userId and ignore this. */
    private String destinationFor(NotificationChannel channel, User user) {
        if (user == null) {
            return null;
        }
        return channel == NotificationChannel.EMAIL ? user.getEmail() : user.getPhone();
    }
}
