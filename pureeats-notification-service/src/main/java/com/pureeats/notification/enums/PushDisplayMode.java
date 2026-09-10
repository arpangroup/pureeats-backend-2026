package com.pureeats.notification.enums;

/**
 * How a PUSH-channel message should present itself on the device - the one thing {@link
 * com.pureeats.notification.enums.NotificationChannel#PUSH} needs that no other channel does,
 * since EMAIL/SMS/IN_APP are always "visible" by definition. Drives whether {@link
 * com.pureeats.notification.service.FcmSender#send} attaches a {@code Notification}/{@code
 * WebpushConfig} block at all - see that class for the mechanics, {@code docs/push-notifications.md}
 * for the full picture of when to use which.
 */
public enum PushDisplayMode {
    /**
     * A plain OS/browser notification the user sees and can tap - title, body, optionally an
     * image/click-link/action buttons. Use for anything the user should be alerted to even if the
     * app isn't open: promotions, "your order was delivered", a new message.
     */
    VISIBLE,
    /**
     * Data-only - no title/body notification block is ever attached, so nothing pops up on any
     * platform. The app's own code (foreground {@code onMessage} or the service worker's {@code
     * onBackgroundMessage}) receives {@code data} and updates its UI directly. Use for anything
     * that's a live state sync rather than something worth interrupting the user for - order status
     * ticking from PREPARING to ON_THE_WAY while they're already looking at the tracking page, a
     * rider's live location, etc.
     */
    SILENT
}
