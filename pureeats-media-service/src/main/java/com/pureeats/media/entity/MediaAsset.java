package com.pureeats.media.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * One row per upload - an audit/history trail regardless of whether the owning entity treats it
 * as its single "current" image (e.g. {@code User.photo} just stores this row's {@code storageKey})
 * or, later, a gallery (multiple rows sharing the same {@code ownerType}/{@code ownerId}).
 */
@Entity
@Table(name = "media_assets", indexes = {
        @Index(name = "idx_media_assets_owner", columnList = "owner_type,owner_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MediaAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "storage_key", nullable = false, unique = true, length = 512)
    private String storageKey;

    @Column(name = "original_filename")
    private String originalFilename;

    @Column(name = "content_type", length = 128)
    private String contentType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "owner_type", nullable = false, length = 32)
    private String ownerType;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "uploaded_by")
    private Long uploadedBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
