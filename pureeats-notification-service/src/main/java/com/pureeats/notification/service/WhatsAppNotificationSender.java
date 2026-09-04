package com.pureeats.notification.service;

import com.pureeats.domain.common.PiiMaskUtil;
import com.pureeats.notification.dto.NotificationRequest;
import com.pureeats.notification.dto.NotificationResult;
import com.pureeats.notification.enums.NotificationChannel;
import com.pureeats.notification.provider.WhatsAppProvider;
import com.pureeats.notification.template.NotificationTemplateResolver;
import com.pureeats.notification.template.TemplateRenderer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Same template-driven shape as {@link SmsNotificationService} - {@code templates/whatsapp/otp.txt}
 * covers every OTP-shaped {@link com.pureeats.notification.enums.NotificationType} via
 * {@link NotificationTemplateResolver}'s generic-OTP fallback, and
 * {@code templates/whatsapp/order-status-update.txt} covers {@code ORDER_STATUS_UPDATE} using the
 * {@code title}/{@code body} params order-service's {@code OrderNotificationService} supplies.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsAppNotificationSender implements ChannelNotificationSender {

    private final WhatsAppProvider whatsAppProvider;
    private final TemplateRenderer templateRenderer;
    private final NotificationTemplateResolver templateResolver;

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.WHATSAPP;
    }

    @Override
    public NotificationResult send(NotificationRequest request) {
        // Never log `message` here - the rendered body may carry an OTP for OTP-type notifications.
        String masked = PiiMaskUtil.maskPhone(request.destination());
        log.info("Sending {} WhatsApp notification to {}", request.type(), masked);
        String message = templateRenderer.render(templateResolver.resolveBody(NotificationChannel.WHATSAPP, request.type(), "txt"), request.params());

        NotificationResult result = whatsAppProvider.send(request.destination(), message);
        if (!result.success()) {
            log.warn("WhatsApp notification {} to {} failed via {}: {}", request.type(), masked,
                    whatsAppProvider.getClass().getSimpleName(), result.failureReason());
        }
        return result;
    }
}
