package com.pureeats.notification.enums;

/**
 * The standing, always-subscribed topic groups every device is auto-enrolled into at token
 * registration time (see {@code SavePushTokenRequest#audience} and {@code PushTokenService#save})
 * - the closed, known set of "which app is this device running." Deliberately NOT the same concept
 * as an arbitrary broadcast/segment topic (a promo campaign, a city, a cuisine preference, ...),
 * which is just a plain topic-name string an admin makes up on the fly - see {@code PushTopics} for
 * those. This enum only covers audiences the backend itself manages automatically.
 */
public enum PushAudience {
    /** Every device registered from the customer-facing React app. */
    CUSTOMER("all_customers"),
    /** Every device registered from the admin panel (admin/employee/restaurant-owner/delivery roles alike - the panel doesn't distinguish at registration time). */
    STAFF("all_staff");

    private final String topic;

    PushAudience(String topic) {
        this.topic = topic;
    }

    /** The standing topic this audience is auto-subscribed to - see {@code PushTopics} for the same constants exposed for direct use when sending (e.g. a "send to every customer" broadcast). */
    public String topic() {
        return topic;
    }
}
