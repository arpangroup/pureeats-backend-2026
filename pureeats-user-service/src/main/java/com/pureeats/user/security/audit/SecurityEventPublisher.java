package com.pureeats.user.security.audit;

/**
 * Every place in the auth flow that cares about "record that this happened" depends only on this.
 * The default implementation persists to {@code audit_logs}; a future Kafka/SNS/SIEM sink is a
 * new implementation of this interface, not a change to any auth service.
 */
public interface SecurityEventPublisher {
    void publish(SecurityEvent event);
}
