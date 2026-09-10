package com.pureeats.notification.dto;

import com.pureeats.notification.enums.PushDisplayMode;

import java.util.List;
import java.util.Map;

/**
 * Everything {@link com.pureeats.notification.service.FcmSender#send} can put into one FCM
 * message. Exactly one of {@code token} (a single device) or {@code topic} (every device
 * subscribed to it - see FcmSender#subscribeToTopic) must be set; the other should be null.
 * <p>
 * {@link #displayMode} decides everything about presentation - {@link PushDisplayMode#SILENT}
 * means no {@code title}/{@code body} ever shows up as a notification block, no matter what's
 * passed here; {@code title}/{@code body} still travel in the data payload either way; so does
 * everything else. Prefer the {@link #visible} / {@link #silent} factories over the canonical
 * constructor at call sites - they read as intent ("this should pop up" vs "this is a quiet state
 * sync") rather than a stray enum argument buried in a long parameter list.
 * <p>
 * {@code data} is delivered to the app's code (foreground {@code onMessage} or the service
 * worker's {@code onBackgroundMessage}) regardless of platform or display mode - use it for
 * anything the app needs to act on, not just display (e.g. {@code orderId} to know what to
 * refetch, or a delivery partner's name/phone once assigned). {@code imageUrl}, {@code
 * clickAction} (the URL opened when the notification itself is clicked - see {@link
 * com.google.firebase.messaging.WebpushFcmOptions#withLink}) and {@code actions} (up to ~2
 * buttons, Chrome/Edge only) are VISIBLE-only presentation - harmless but ignored when {@code
 * displayMode} is SILENT, since there's no notification for them to attach to.
 */
public record FcmPushRequest(
        String token,
        String topic,
        String title,
        String body,
        PushDisplayMode displayMode,
        String imageUrl,
        Map<String, String> data,
        String clickAction,
        List<FcmAction> actions
) {
    public FcmPushRequest {
        data = data == null ? Map.of() : Map.copyOf(data);
        actions = actions == null ? List.of() : List.copyOf(actions);
    }

    /** A real notification the user should see and can tap - promotions, "your order was delivered", anything worth interrupting them for. */
    public static FcmPushRequest visible(String token, String topic, String title, String body, String imageUrl, Map<String, String> data, String clickAction, List<FcmAction> actions) {
        return new FcmPushRequest(token, topic, title, body, PushDisplayMode.VISIBLE, imageUrl, data, clickAction, actions);
    }

    /** A quiet state sync - nothing pops up on any platform, only {@code data} is delivered. Use for anything the app should react to live without interrupting the user (an order status ticking forward while they're already looking at it). */
    public static FcmPushRequest silent(String token, String topic, Map<String, String> data) {
        return new FcmPushRequest(token, topic, null, null, PushDisplayMode.SILENT, null, data, null, null);
    }
}
