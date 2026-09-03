package com.pureeats.notification.config;

import com.pureeats.notification.provider.ConsoleEmailProvider;
import com.pureeats.notification.provider.ConsoleSmsProvider;
import com.pureeats.notification.provider.ConsoleWhatsAppProvider;
import com.pureeats.notification.provider.EmailProvider;
import com.pureeats.notification.provider.SmsProvider;
import com.pureeats.notification.provider.SmtpEmailProvider;
import com.pureeats.notification.provider.WhatsAppProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Chooses the active {@link EmailProvider}/{@link SmsProvider}/{@link WhatsAppProvider}
 * implementation purely from configuration ({@code notification.email-provider}/
 * {@code notification.sms-provider}/{@code notification.whatsapp-provider}) - the rest of the
 * notification module, and every caller of {@code NotificationService}, only ever sees the
 * interface. Adding e.g. SendGrid as a new email sender means writing one class implementing
 * {@link EmailProvider} and one more {@code @Bean} method here (guarded by
 * {@code @ConditionalOnProperty(..., havingValue = "sendgrid")}) - {@code EmailNotificationService}
 * and every OTP/order/rating call site keep depending on {@code NotificationService} and never
 * change. Same story for a new SMS gateway (Twilio/MSG91): one {@link SmsProvider} implementation
 * + one bean here.
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

    // To add SendGrid (or any other email API) without touching EmailNotificationService or any
    // caller: implement EmailProvider in a new SendGridEmailProvider class, then uncomment/adapt:
    //
    // @Bean
    // @ConditionalOnProperty(prefix = "notification", name = "email-provider", havingValue = "sendgrid")
    // public EmailProvider sendGridEmailProvider(@Value("${notification.sendgrid.api-key}") String apiKey) {
    //     return new SendGridEmailProvider(apiKey);
    // }

    @Bean
    @ConditionalOnProperty(prefix = "notification", name = "sms-provider", havingValue = "console", matchIfMissing = true)
    public SmsProvider consoleSmsProvider() {
        return new ConsoleSmsProvider();
    }

    // Same pattern for a new SMS gateway, e.g. Twilio:
    //
    // @Bean
    // @ConditionalOnProperty(prefix = "notification", name = "sms-provider", havingValue = "twilio")
    // public SmsProvider twilioSmsProvider(TwilioProperties properties) {
    //     return new TwilioSmsProvider(properties);
    // }

    @Bean
    @ConditionalOnProperty(prefix = "notification", name = "whatsapp-provider", havingValue = "console", matchIfMissing = true)
    public WhatsAppProvider consoleWhatsAppProvider() {
        return new ConsoleWhatsAppProvider();
    }
}
