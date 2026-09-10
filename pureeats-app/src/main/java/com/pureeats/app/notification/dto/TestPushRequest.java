package com.pureeats.app.notification.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Body for POST /api/v1/admin/notifications/test/push - see AdminNotificationTestController.
 * {@code userId} is the target - pushes fan out to every active device token that user has
 * registered (see PushNotificationSender), there's no way to target a single device directly.
 * title/body are optional - blank falls back to generic test copy.
 */
public record TestPushRequest(@NotNull Long userId, String title, String body) {
}
