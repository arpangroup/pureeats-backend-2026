package com.pureeats.notification.service;

import com.pureeats.notification.dto.NotificationRequest;
import com.pureeats.notification.dto.NotificationResult;
import com.pureeats.notification.enums.NotificationChannel;
import com.pureeats.notification.enums.NotificationType;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * The one bean auth/order/rating code depends on to notify a user on any channel - email, SMS,
 * push, WhatsApp, in-app, console, or whatever gets added later. No module outside
 * {@code pureeats-notification-service} ever imports {@code EmailProvider}/{@code SmsProvider}/
 * {@code PushTokenRepository}/JavaMailSender directly.
 */
public interface NotificationService {
    /** Blocks the calling thread until the provider round-trip completes. Prefer {@link #sendAsync} on any request-handling path - a slow SMTP/SMS/push provider otherwise directly adds to that request's latency. */
    NotificationResult send(NotificationRequest request);

    /**
     * Same as {@link #send}, but runs the actual provider call on a background pool
     * ({@code notificationTaskExecutor}) instead of the caller's thread - the returned future
     * completes independently of whatever HTTP request triggered it. Use this from any
     * request-handling code path; reserve {@link #send} for background jobs/tests that already run
     * off the request thread and want a synchronous result.
     */
    CompletableFuture<NotificationResult> sendAsync(NotificationRequest request);

    /**
     * Fans the same logical notification out to several channels at once - e.g. a login OTP that
     * should hit EMAIL (the actual code) and CONSOLE (a dev-visible trace) and PUSH (a "new sign-in"
     * heads-up) simultaneously, or an order-status update that should hit PUSH and IN_APP together.
     * Each channel is dispatched independently (one channel failing never blocks another), so the
     * result map should be inspected per-channel rather than treated as all-or-nothing. Blocks the
     * calling thread the same way {@link #send} does - prefer {@link #sendToChannelsAsync} on a
     * request-handling path.
     */
    Map<NotificationChannel, NotificationResult> sendToChannels(NotificationType type, String destination, Long userId,
                                                                  Map<String, Object> params, Set<NotificationChannel> channels);

    /** Background-pool version of {@link #sendToChannels} - fire-and-forget from the caller's perspective, each channel's outcome is still logged by its own sender. */
    void sendToChannelsAsync(NotificationType type, String destination, Long userId,
                              Map<String, Object> params, Set<NotificationChannel> channels);
}
