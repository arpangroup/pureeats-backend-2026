package com.pureeats.app.notification.dto;

import jakarta.validation.constraints.NotNull;

/** Body for POST /api/v1/admin/notifications/topics/{topic}/subscribe and .../unsubscribe - every active device token the given user has registered gets subscribed/unsubscribed to that topic in one call. */
public record TopicSubscribeRequest(@NotNull Long userId) {
}
