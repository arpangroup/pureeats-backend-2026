package com.pureeats.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Mirrors Spatie laravel-permission's polymorphic "model_has_roles" pivot:
 * (roleId, modelType, modelId) with a composite id, no surrogate key in the legacy schema.
 */
@Entity
@Table(name = "model_has_roles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@IdClass(ModelHasRole.ModelHasRoleId.class)
public class ModelHasRole {

    @Id
    @Column(name = "role_id", nullable = false)
    private Long roleId;

    @Id
    @Column(name = "model_type", nullable = false)
    private String modelType;

    @Id
    @Column(name = "model_id", nullable = false)
    private Long modelId;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class ModelHasRoleId implements java.io.Serializable {
        private Long roleId;
        private String modelType;
        private Long modelId;
    }
}
