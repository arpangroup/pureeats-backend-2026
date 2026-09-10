package com.pureeats.app.notification.dto;

import com.pureeats.notification.dto.FcmAction;

import java.util.List;
import java.util.Map;

/**
 * Body for POST /api/v1/admin/notifications/test/push - see AdminNotificationTestController.
 * Exactly one of {@code userId} or {@code topic} must be set: {@code userId} fans out to every
 * active device token that user has registered (and also records an IN_APP alert, so it shows in
 * their notification bell); {@code topic} sends once to every device subscribed to that topic (see
 * POST .../topics/{topic}/subscribe) and has no notion of a single "recipient" to attach a bell
 * entry to, so it skips IN_APP entirely.
 * <p>
 * title/body are optional - blank falls back to generic test copy. imageUrl/clickAction/actions are
 * the web-specific extras (see FcmSender) - all optional, a plain title+body push if omitted.
 */
public record TestPushRequest(
        Long userId,
        String topic,
        String title,
        String body,
        String imageUrl,
        String clickAction,
        Map<String, String> data,
        List<FcmAction> actions
) {
}
