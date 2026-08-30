package com.pureeats.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** All operational knobs for outbound notifications - see application.yml under {@code notification.*}. */
@ConfigurationProperties(prefix = "notification")
public class NotificationProperties {

    /** {@code smtp} or {@code console}. Console never opens a network connection - safe default for local dev/tests. */
    private String emailProvider = "console";

    /** {@code console} today; a real gateway (e.g. {@code twilio}) can be registered under this key later. */
    private String smsProvider = "console";

    private String fromAddress = "no-reply@pureeats.local";
    private String fromName = "PureEats";

    public String getEmailProvider() {
        return emailProvider;
    }

    public void setEmailProvider(String emailProvider) {
        this.emailProvider = emailProvider;
    }

    public String getSmsProvider() {
        return smsProvider;
    }

    public void setSmsProvider(String smsProvider) {
        this.smsProvider = smsProvider;
    }

    public String getFromAddress() {
        return fromAddress;
    }

    public void setFromAddress(String fromAddress) {
        this.fromAddress = fromAddress;
    }

    public String getFromName() {
        return fromName;
    }

    public void setFromName(String fromName) {
        this.fromName = fromName;
    }
}
