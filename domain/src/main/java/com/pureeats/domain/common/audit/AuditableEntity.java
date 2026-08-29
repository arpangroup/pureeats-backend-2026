package com.pureeats.domain.common.audit;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;

import java.time.Instant;

@MappedSuperclass // it indicate its a super class, not a JPA entity, hence no extra table
@EntityListeners(AuditingEntityListener.class)
@Getter
public abstract class AuditableEntity {
    //@CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    //@LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    //@CreatedBy
    //@Column(nullable = false, updatable = false)
    private Long createdBy; // Why not string? String is Flexible but weak type safety

    //@LastModifiedBy
    //@Column(nullable = false)
    private Long lastUpdatedBy;

    /**
     * Used only by AuditingEntityListener. Package-private intentionally.
     */
    void auditCreate(Long userId, Instant now) {
        this.createdAt = now;
        this.updatedAt = now;
        this.createdBy = userId;
        this.lastUpdatedBy = userId;
    }

    /**
     * Used only by AuditingEntityListener. Package-private intentionally.
     */
    void auditUpdate(Long userId, Instant now) {
        this.updatedAt = now;
        this.lastUpdatedBy = userId;
    }
}
