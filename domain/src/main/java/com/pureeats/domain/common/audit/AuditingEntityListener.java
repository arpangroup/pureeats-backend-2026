package com.pureeats.domain.common.audit;

import com.pureeats.domain.common.CurrentUserContext;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

import java.time.Instant;

public class AuditingEntityListener {
    @PrePersist
    public void onCreate(AuditableEntity entity) {
        Instant now = Instant.now();
        Long currentUserId = CurrentUserContext.get();
        entity.auditCreate(currentUserId, now);
    }

    @PreUpdate
    public void onUpdate(AuditableEntity entity) {
        Instant now = Instant.now();
        Long currentUserId = CurrentUserContext.get();
        entity.auditUpdate(currentUserId, now);
    }
}
