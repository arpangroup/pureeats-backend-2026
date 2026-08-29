package com.pureeats.notification.service;

import com.pureeats.domain.common.PiiMaskUtil;
import com.pureeats.notification.entity.NotificationLog;
import com.pureeats.notification.enums.NotificationChannel;
import com.pureeats.notification.enums.NotificationStatus;
import com.pureeats.notification.dto.NotificationRequest;
import com.pureeats.notification.dto.NotificationResult;
import com.pureeats.notification.provider.EmailProvider;
import com.pureeats.notification.repository.NotificationLogRepository;
import com.pureeats.notification.template.NotificationTemplateResolver;
import com.pureeats.notification.template.TemplateRenderer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class EmailNotificationService implements ChannelNotificationSender {

    private final EmailProvider emailProvider;
    private final TemplateRenderer templateRenderer;
    private final NotificationTemplateResolver templateResolver;
    private final NotificationLogRepository notificationLogRepository;

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.EMAIL;
    }

    @Override
    @Transactional
    public NotificationResult send(NotificationRequest request) {
        String subject = templateResolver.resolveSubject(request.type());
        String html = templateRenderer.render(templateResolver.resolveBody(NotificationChannel.EMAIL, request.type(), "html"), request.params());
        String text = templateRenderer.render(templateResolver.resolveBody(NotificationChannel.EMAIL, request.type(), "txt"), request.params());

        NotificationResult result = emailProvider.send(request.destination(), subject, html, text);
        logAttempt(request, result);
        return result;
    }

    private void logAttempt(NotificationRequest request, NotificationResult result) {
        NotificationLog logEntry = new NotificationLog();
        logEntry.setNotificationType(request.type());
        logEntry.setChannel(NotificationChannel.EMAIL);
        logEntry.setDestinationMasked(PiiMaskUtil.maskEmail(request.destination()));
        logEntry.setProvider(emailProvider.getClass().getSimpleName());
        logEntry.setStatus(result.success() ? NotificationStatus.SENT : NotificationStatus.FAILED);
        logEntry.setProviderMessageId(result.providerMessageId());
        logEntry.setFailureReason(result.failureReason());
        logEntry.setCreatedAt(LocalDateTime.now());
        logEntry.setSentAt(result.success() ? LocalDateTime.now() : null);
        notificationLogRepository.save(logEntry);
    }
}
