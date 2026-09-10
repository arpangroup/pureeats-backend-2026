package com.pureeats.order.service;

import com.pureeats.domain.entity.User;
import com.pureeats.notification.dto.NotificationRequest;
import com.pureeats.notification.enums.NotificationChannel;
import com.pureeats.notification.enums.NotificationRecipientRole;
import com.pureeats.notification.enums.NotificationType;
import com.pureeats.notification.service.NotificationRoutingService;
import com.pureeats.notification.service.NotificationService;
import com.pureeats.user.repository.UserRepository;
import com.pureeats.user.service.RoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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
    private final RoleService roleService;

    private static final List<com.pureeats.domain.enums.Role> ADMIN_LIKE_ROLES =
            List.of(com.pureeats.domain.enums.Role.SUPER_ADMIN, com.pureeats.domain.enums.Role.ADMIN, com.pureeats.domain.enums.Role.EMPLOYEE);

    /** Visible everywhere it's routed - a real notification the recipient sees and can tap. Use {@link #notify(NotificationRecipientRole, Long, String, String, Map)} instead for a transition the customer's own UI should just reflect live, without interrupting them. */
    @Transactional(readOnly = true)
    public void notify(NotificationRecipientRole role, Long userId, String title, String body) {
        dispatch(role, userId, title, body, false, Map.of());
    }

    /**
     * Same dispatch as the 4-arg {@link #notify}, plus two things specific to a live order-status
     * sync: the PUSH channel goes out {@link com.pureeats.notification.enums.PushDisplayMode#SILENT}
     * (never pops up - see that enum), and {@code data} rides alongside on every channel that reads
     * it (currently just PUSH) so the client can update its UI from the payload alone - {@code
     * orderId}/{@code status} at minimum, plus whatever's specific to this transition (e.g. the
     * newly-assigned delivery partner's name/phone/photo). EMAIL/SMS/IN_APP are unaffected by
     * "silent" - that only changes how PUSH presents itself; IN_APP still records a normal bell
     * entry so the customer has a history to look back at even though nothing popped up live.
     */
    @Transactional(readOnly = true)
    public void notify(NotificationRecipientRole role, Long userId, String title, String body, Map<String, Object> data) {
        dispatch(role, userId, title, body, true, data);
    }

    /**
     * Dispatches every configured channel via {@link NotificationService#sendAsync} - the caller
     * (an order accept/cancel/assign/deliver action) gets its HTTP response as soon as the order
     * itself is updated, without waiting on an SMTP/SMS/push round-trip. Each channel's outcome is
     * still logged, just from whichever background thread it completes on rather than this one.
     */
    private void dispatch(NotificationRecipientRole role, Long userId, String title, String body, boolean silentPush, Map<String, Object> data) {
        Set<NotificationChannel> channels = notificationRoutingService.orderStatusChannelsFor(role);
        if (channels.isEmpty()) {
            log.debug("No channels configured for role {} - skipping order notification '{}' to user {}", role, title, userId);
            return;
        }
        Map<String, Object> params = new java.util.HashMap<>(Map.of("title", title, "body", body, "category", "ORDER_UPDATE"));
        if (silentPush) params.put("silent", true);
        if (!data.isEmpty()) params.put("data", data);
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

    /**
     * Alerts every SUPER_ADMIN/ADMIN/EMPLOYEE user that a brand-new order was placed - see {@link
     * #notifyNewOrder} for why this bypasses {@link NotificationRoutingService} entirely. Restaurant
     * owners get the same treatment per-order via {@link #notifyNewOrder} directly (see
     * OrderService#notifyOwners), since that's one specific owner per order, not "everyone in a role".
     */
    @Transactional(readOnly = true)
    public void notifyAdminsOfNewOrder(String title, String body, Map<String, Object> data) {
        List<Long> adminIds = roleService.findUserIdsInAnyRole(ADMIN_LIKE_ROLES);
        if (adminIds.isEmpty()) {
            log.debug("No admin/employee users to notify of new order '{}'", title);
            return;
        }
        for (Long adminId : adminIds) {
            notifyNewOrder(adminId, title, body, data);
        }
    }

    /**
     * Alerts one user (an admin/employee, or a restaurant owner about their own restaurant's order)
     * that a brand-new order needs attention - always PUSH (visible - this should interrupt
     * whoever's watching the admin panel) + IN_APP, bypassing {@link NotificationRoutingService}
     * entirely rather than looking up per-role channel config. Unlike an order-status transition
     * (which is about one customer and fully admin-tunable per recipient role), "a new order needs
     * attention" is a fixed operational alert, not a configurable customer-communication preference.
     * Whether a given browser *listens* for this via push vs. polls for it instead is a client-side,
     * per-device choice (see the admin panel's New order alert settings) - this always fires the
     * same way regardless.
     */
    @Transactional(readOnly = true)
    public void notifyNewOrder(Long userId, String title, String body, Map<String, Object> data) {
        Map<String, Object> params = new java.util.HashMap<>(Map.of("title", title, "body", body, "category", "NEW_ORDER"));
        if (!data.isEmpty()) params.put("data", data);
        notificationService.sendToChannelsAsync(NotificationType.NEW_ORDER, null, userId, params,
                Set.of(NotificationChannel.PUSH, NotificationChannel.IN_APP));
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
