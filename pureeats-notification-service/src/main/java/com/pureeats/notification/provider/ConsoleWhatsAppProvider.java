package com.pureeats.notification.provider;

import com.pureeats.domain.common.PiiMaskUtil;
import com.pureeats.notification.dto.NotificationResult;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

/**
 * Default WhatsApp "provider" - no WhatsApp Business API credentials exist in this codebase, so
 * this prints the message instead of sending it, same accepted dev-only shape as
 * {@link ConsoleSmsProvider}. Replace with a real {@code WhatsAppProvider} implementation and set
 * {@code notification.whatsapp-provider} accordingly to go live.
 */
@Slf4j
public class ConsoleWhatsAppProvider implements WhatsAppProvider {

    @Override
    public NotificationResult send(String phoneNumber, String message) {
        String masked = PiiMaskUtil.maskPhone(phoneNumber);
        System.out.println("[DEV WHATSAPP] to=" + masked + " message=" + message);
        log.info("[DEV WHATSAPP] queued for {} (console provider, no WhatsApp gateway configured)", masked);
        return NotificationResult.success("console-" + UUID.randomUUID());
    }
}
