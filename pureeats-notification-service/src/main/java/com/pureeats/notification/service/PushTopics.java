package com.pureeats.notification.service;

import com.pureeats.notification.enums.PushAudience;

/**
 * Well-known FCM topic names, kept in one place so nothing hardcodes the string more than once.
 * {@link #ALL_CUSTOMERS}/{@link #ALL_STAFF} mirror {@link PushAudience} - every device is
 * auto-subscribed to its own audience's topic at registration (see {@code PushTokenService#save}),
 * so a broadcast to "every customer" is just a send to {@link #ALL_CUSTOMERS}, no per-user loop.
 * <p>
 * A topic is otherwise just a string FCM has never heard of until something subscribes to it -
 * there's no fixed enum of every possible topic, since a segment (a city, a cuisine preference, a
 * promo campaign audience, ...) is defined by whoever's building that campaign, not by this class.
 * {@link #segment} exists only so those ad-hoc names stay consistent instead of each caller
 * inventing its own prefix/separator convention.
 */
public final class PushTopics {

    public static final String ALL_CUSTOMERS = PushAudience.CUSTOMER.topic();
    public static final String ALL_STAFF = PushAudience.STAFF.topic();

    private PushTopics() {
    }

    /**
     * Builds a consistent name for a segment topic that isn't one of the standing audiences above -
     * e.g. {@code segment("city", "bangalore")} -> {@code "segment_city_bangalore"}. Nothing
     * subscribes devices to these automatically; that's on whoever defines the segment (e.g. an
     * admin campaign feature, once one exists) to call {@code FcmSender#subscribeToTopic} for the
     * matching users' tokens before sending to it.
     */
    public static String segment(String dimension, String value) {
        String normalized = (dimension + "_" + value).toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_]+", "_");
        return "segment_" + normalized;
    }
}
