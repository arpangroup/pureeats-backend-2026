package com.pureeats.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "model_has_permissions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@IdClass(ModelHasPermission.ModelHasPermissionId.class)
public class ModelHasPermission {

    @Id
    @Column(name = "permission_id", nullable = false)
    private Long permissionId;

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
    public static class ModelHasPermissionId implements java.io.Serializable {
        private Long permissionId;
        private String modelType;
        private Long modelId;
    }
}
