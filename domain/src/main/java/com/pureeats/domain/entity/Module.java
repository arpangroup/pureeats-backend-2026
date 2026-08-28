package com.pureeats.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "modules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Module {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Lob
    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "version", nullable = false)
    private String version;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "is_installed", nullable = false)
    private Boolean isInstalled;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "short_name", nullable = false)
    private String shortName;

    @Column(name = "code", nullable = false)
    private String code;

    @Lob
    @Column(name = "settings_path")
    private String settingsPath;

    @Column(name = "update_date", nullable = false)
    private LocalDateTime updateDate;
}
