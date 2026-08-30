package com.pureeats.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "role_has_permissions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@IdClass(RoleHasPermission.RoleHasPermissionId.class)
public class RoleHasPermission {

    @Id
    @Column(name = "permission_id", nullable = false)
    private Long permissionId;

    @Id
    @Column(name = "role_id", nullable = false)
    private Long roleId;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class RoleHasPermissionId implements java.io.Serializable {
        private Long permissionId;
        private Long roleId;
    }
}
