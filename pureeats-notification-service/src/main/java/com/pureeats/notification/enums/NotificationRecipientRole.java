package com.pureeats.notification.enums;

/** Who an order-status-change notification is being sent to - the key {@link com.pureeats.notification.service.NotificationRoutingService} looks up channel config by. */
public enum NotificationRecipientRole {
    CUSTOMER,
    STORE_OWNER,
    DELIVERY_PARTNER,
    ADMIN
}
