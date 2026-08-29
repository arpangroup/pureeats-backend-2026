package com.pureeats.notification.config;

import com.pureeats.notification.provider.ConsoleEmailProvider;
import com.pureeats.notification.provider.ConsoleSmsProvider;
import com.pureeats.notification.provider.EmailProvider;
import com.pureeats.notification.provider.SmsProvider;
import com.pureeats.notification.provider.SmtpEmailProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Chooses the active {@link EmailProvider}/{@link SmsProvider} implementation purely from
 * configuration ({@code notification.email-provider}/{@code notification.sms-provider}) - the
 * rest of the notification module, and every caller of {@code NotificationService}, only ever
 * sees the interface.
 */
@Configuration
@EnableConfigurationProperties(NotificationProperties.class)
public class NotificationProviderConfig {

    @Bean
    @ConditionalOnProperty(prefix = "notification", name = "email-provider", havingValue = "smtp")
    public EmailProvider smtpEmailProvider(JavaMailSender mailSender, NotificationProperties properties) {
        return new SmtpEmailProvider(mailSender, properties);
    }

    @Bean
    @ConditionalOnProperty(prefix = "notification", name = "email-provider", havingValue = "console", matchIfMissing = true)
    public EmailProvider consoleEmailProvider() {
        return new ConsoleEmailProvider();
    }

    @Bean
    @ConditionalOnProperty(prefix = "notification", name = "sms-provider", havingValue = "console", matchIfMissing = true)
    public SmsProvider consoleSmsProvider() {
        return new ConsoleSmsProvider();
    }
}
