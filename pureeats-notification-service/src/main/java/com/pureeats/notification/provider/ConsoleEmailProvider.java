package com.pureeats.notification.provider;

import com.pureeats.domain.common.PiiMaskUtil;
import com.pureeats.notification.dto.NotificationResult;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

/**
 * Local-development stand-in for a real email gateway - prints the rendered message to the
 * console instead of connecting to SMTP. This is the one deliberate exception to "never log an
 * OTP": with no SMTP credentials configured, this console line IS the delivery mechanism (there
 * is no other way to complete the flow). Never active unless {@code notification.email-provider=console}
 * (the default) - set it to {@code smtp} in production.
 */
@Slf4j
public class ConsoleEmailProvider implements EmailProvider {

    @Override
    public NotificationResult send(String to, String subject, String htmlBody, String textBody) {
        String masked = PiiMaskUtil.maskEmail(to);
        System.out.println("=".repeat(60));
        System.out.println("[DEV EMAIL] to=" + masked + " subject=" + subject);
        System.out.println(textBody != null ? textBody : htmlBody);
        System.out.println("=".repeat(60));
        log.info("[DEV EMAIL] queued for {} (console provider, no SMTP configured)", masked);
        return NotificationResult.success("console-" + UUID.randomUUID());
    }
}
