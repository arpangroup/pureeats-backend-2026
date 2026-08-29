package com.pureeats.notification.service;

import com.pureeats.domain.common.PiiMaskUtil;
import com.pureeats.notification.entity.NotificationLog;
import com.pureeats.notification.enums.NotificationChannel;
import com.pureeats.notification.enums.NotificationStatus;
import com.pureeats.notification.dto.NotificationRequest;
import com.pureeats.notification.dto.NotificationResult;
import com.pureeats.notification.provider.SmsProvider;
import com.pureeats.notification.repository.NotificationLogRepository;
import com.pureeats.notification.template.NotificationTemplateResolver;
import com.pureeats.notification.template.TemplateRenderer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SmsNotificationService implements ChannelNotificationSender {

    private final SmsProvider smsProvider;
    private final TemplateRenderer templateRenderer;
    private final NotificationTemplateResolver templateResolver;
    private final NotificationLogRepository notificationLogRepository;

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.SMS;
    }

    @Override
    @Transactional
    public NotificationResult send(NotificationRequest request) {
        String message = templateRenderer.render(templateResolver.resolveBody(NotificationChannel.SMS, request.type(), "txt"), request.params());

        NotificationResult result = smsProvider.send(request.destination(), message);
        logAttempt(request, result);
        return result;
    }

    private void logAttempt(NotificationRequest request, NotificationResult result) {
        NotificationLog logEntry = new NotificationLog();
        logEntry.setNotificationType(request.type());
        logEntry.setChannel(NotificationChannel.SMS);
        logEntry.setDestinationMasked(PiiMaskUtil.maskPhone(request.destination()));
        logEntry.setProvider(smsProvider.getClass().getSimpleName());
        logEntry.setStatus(result.success() ? NotificationStatus.SENT : NotificationStatus.FAILED);
        logEntry.setProviderMessageId(result.providerMessageId());
        logEntry.setFailureReason(result.failureReason());
        logEntry.setCreatedAt(LocalDateTime.now());
        logEntry.setSentAt(result.success() ? LocalDateTime.now() : null);
        notificationLogRepository.save(logEntry);
    }
}
