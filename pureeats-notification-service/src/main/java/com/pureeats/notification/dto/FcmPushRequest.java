package com.pureeats.notification.dto;

import java.util.List;
import java.util.Map;

/**
 * Everything {@link com.pureeats.notification.service.FcmSender#send} can put into one FCM
 * message. Exactly one of {@code token} (a single device) or {@code topic} (every device
 * subscribed to it - see FcmSender#subscribeToTopic) must be set; the other should be null.
 *
 * {@code data} is delivered to the app's code (foreground {@code onMessage} or the service
 * worker's {@code onBackgroundMessage}) regardless of platform - use it for anything the app needs
 * to act on, not just display (e.g. {@code orderId} to know what to refetch). {@code imageUrl},
 * {@code clickAction} (the URL opened when the notification itself is clicked - see
 * {@link com.google.firebase.messaging.WebpushFcmOptions#withLink}) and {@code actions} (up to ~2
 * buttons, Chrome/Edge only - other browsers ignore them) are web-specific presentation, mirrored
 * onto both the plain {@code Notification} (image only, for non-web platforms/fallback) and the
 * {@code WebpushConfig} (full fidelity).
 */
public record FcmPushRequest(
        String token,
        String topic,
        String title,
        String body,
        String imageUrl,
        Map<String, String> data,
        String clickAction,
        List<FcmAction> actions
) {
    public FcmPushRequest {
        data = data == null ? Map.of() : Map.copyOf(data);
        actions = actions == null ? List.of() : List.copyOf(actions);
    }
}
