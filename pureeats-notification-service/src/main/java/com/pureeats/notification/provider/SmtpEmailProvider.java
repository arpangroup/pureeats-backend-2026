package com.pureeats.notification.provider;

import com.pureeats.notification.config.NotificationProperties;
import com.pureeats.notification.dto.NotificationResult;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

/**
 * Real Gmail/SMTP delivery via Spring's {@link JavaMailSender} (auto-configured from
 * {@code spring.mail.*} - see the README for Gmail App Password setup). Never logs the message
 * body (which may contain an OTP) - only the outcome.
 */
@Slf4j
@RequiredArgsConstructor
public class SmtpEmailProvider implements EmailProvider {

    private final JavaMailSender mailSender;
    private final NotificationProperties properties;

    @Override
    public NotificationResult send(String to, String subject, String htmlBody, String textBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setFrom(properties.getFromAddress(), properties.getFromName());
            helper.setText(textBody != null ? textBody : "", htmlBody != null ? htmlBody : "");
            mailSender.send(message);
            return NotificationResult.success(message.getMessageID());
        } catch (MailException | java.io.UnsupportedEncodingException | jakarta.mail.MessagingException e) {
            log.warn("Email delivery failed to a masked recipient: {}", e.getMessage());
            return NotificationResult.failure(e.getClass().getSimpleName());
        }
    }
}
