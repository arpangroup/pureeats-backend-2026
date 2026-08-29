package com.pureeats.notification.provider;

import com.pureeats.domain.common.PiiMaskUtil;
import com.pureeats.notification.dto.NotificationResult;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

/**
 * Default SMS "provider" - no Twilio/MSG91 credentials exist in this codebase, so this prints the
 * message instead of sending it. See {@link ConsoleEmailProvider} for why printing the OTP here
 * (rather than to the structured app logger) is the accepted dev-only exception to "never log an
 * OTP". Replace with a real {@code SmsProvider} implementation and set
 * {@code notification.sms-provider} accordingly to go live.
 */
@Slf4j
public class ConsoleSmsProvider implements SmsProvider {

    @Override
    public NotificationResult send(String phoneNumber, String message) {
        String masked = PiiMaskUtil.maskPhone(phoneNumber);
        System.out.println("[DEV SMS] to=" + masked + " message=" + message);
        log.info("[DEV SMS] queued for {} (console provider, no SMS gateway configured)", masked);
        return NotificationResult.success("console-" + UUID.randomUUID());
    }
}
