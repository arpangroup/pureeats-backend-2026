package com.pureeats.notification.template;

import com.pureeats.notification.enums.NotificationChannel;
import com.pureeats.notification.enums.NotificationType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Loads template bodies from {@code classpath:templates/{channel}/{type}.{html|txt}}, falling
 * back to a generic {@code otp.{html|txt}} for any OTP-shaped notification type that doesn't have
 * its own file yet - so adding {@code PASSWORD_RESET_OTP} wording later is "drop in a file", not
 * a code change. Subjects come from {@code templates/email/subjects.properties}.
 */
@Slf4j
@Component
public class NotificationTemplateResolver {

    private static final ConcurrentMap<String, String> TEMPLATE_CACHE = new ConcurrentHashMap<>();
    private volatile Properties subjects;

    public String resolveBody(NotificationChannel channel, NotificationType type, String extension) {
        String channelDir = channel.name().toLowerCase(Locale.ROOT);
        String specific = "templates/" + channelDir + "/" + fileStem(type) + "." + extension;
        String content = load(specific);
        if (content != null) {
            return content;
        }
        String generic = "templates/" + channelDir + "/otp." + extension;
        content = load(generic);
        if (content == null) {
            log.warn("No notification template found for channel={} type={} (tried {} and {})", channel, type, specific, generic);
            return "";
        }
        return content;
    }

    public String resolveSubject(NotificationType type) {
        return subjects().getProperty(type.name(), "PureEats verification code");
    }

    private String fileStem(NotificationType type) {
        return type.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private String load(String classpathLocation) {
        return TEMPLATE_CACHE.computeIfAbsent(classpathLocation, this::readOrNull);
    }

    private String readOrNull(String classpathLocation) {
        ClassPathResource resource = new ClassPathResource(classpathLocation);
        if (!resource.exists()) {
            return null;
        }
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("Failed reading notification template {}: {}", classpathLocation, e.getMessage());
            return null;
        }
    }

    private Properties subjects() {
        Properties loaded = subjects;
        if (loaded == null) {
            loaded = new Properties();
            ClassPathResource resource = new ClassPathResource("templates/email/subjects.properties");
            if (resource.exists()) {
                try (InputStream in = resource.getInputStream()) {
                    loaded.load(in);
                } catch (IOException e) {
                    log.warn("Failed reading notification subjects.properties: {}", e.getMessage());
                }
            }
            subjects = loaded;
        }
        return loaded;
    }
}
