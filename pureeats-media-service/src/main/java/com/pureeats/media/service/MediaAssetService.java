package com.pureeats.media.service;

import com.pureeats.domain.common.exception.BadRequestException;
import com.pureeats.domain.common.exception.ForbiddenException;
import com.pureeats.domain.common.exception.ResourceNotFoundException;
import com.pureeats.media.dto.MediaUploadResponse;
import com.pureeats.media.entity.MediaAsset;
import com.pureeats.media.repository.MediaAssetRepository;
import com.pureeats.media.storage.MediaStorage;
import com.pureeats.media.storage.MediaUrlResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Generic, owner-agnostic upload pipeline: validate -> store bytes -> record a {@link MediaAsset}
 * row -> return a resolved URL. Deliberately has no ownership/authorization logic - callers
 * (e.g. a controller that already knows "this restaurant belongs to this store owner") are
 * responsible for deciding whether the caller may upload for the given owner.
 */
@Service
@RequiredArgsConstructor
public class MediaAssetService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final MediaAssetRepository mediaAssetRepository;
    private final MediaStorage mediaStorage;
    private final MediaUrlResolver mediaUrlResolver;

    @Value("${pureeats.media.max-size-bytes:5242880}")
    private long maxSizeBytes;

    @Transactional
    public MediaUploadResponse upload(MultipartFile file, String ownerType, Long ownerId, Long uploadedBy) {
        return upload(file, ownerType, ownerId, uploadedBy, maxSizeBytes);
    }

    /** Same as {@link #upload(MultipartFile, String, Long, Long)}, but with a caller-chosen size cap tighter than the module default. */
    @Transactional
    public MediaUploadResponse upload(MultipartFile file, String ownerType, Long ownerId, Long uploadedBy, long maxBytesOverride) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("No file was uploaded");
        }
        if (file.getSize() > maxBytesOverride) {
            throw new BadRequestException("Image must be smaller than " + (maxBytesOverride / (1024 * 1024)) + "MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new BadRequestException("Only JPEG, PNG, or WebP images are allowed");
        }

        String extension = extensionFor(contentType);
        String storageKey = ownerType.toLowerCase() + "/" + UUID.randomUUID() + extension;

        try {
            mediaStorage.store(file, storageKey);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store uploaded file", e);
        }

        MediaAsset asset = new MediaAsset();
        asset.setStorageKey(storageKey);
        asset.setOriginalFilename(file.getOriginalFilename());
        asset.setContentType(contentType);
        asset.setSizeBytes(file.getSize());
        asset.setOwnerType(ownerType);
        asset.setOwnerId(ownerId);
        asset.setUploadedBy(uploadedBy);
        asset.setCreatedAt(LocalDateTime.now());
        asset = mediaAssetRepository.save(asset);

        return new MediaUploadResponse(asset.getId(), storageKey, mediaUrlResolver.resolve(storageKey));
    }

    @Transactional(readOnly = true)
    public List<MediaAsset> listForOwner(String ownerType, Long ownerId) {
        return mediaAssetRepository.findByOwnerTypeAndOwnerIdOrderByCreatedAtDesc(ownerType, ownerId);
    }

    @Transactional(readOnly = true)
    public long countForOwner(String ownerType, Long ownerId) {
        return mediaAssetRepository.countByOwnerTypeAndOwnerId(ownerType, ownerId);
    }

    /** Deletes both the stored bytes and the record, after confirming the asset actually belongs to {@code ownerType}/{@code ownerId}. */
    @Transactional
    public void delete(String ownerType, Long ownerId, Long mediaId) {
        MediaAsset asset = mediaAssetRepository.findById(mediaId)
                .orElseThrow(() -> new ResourceNotFoundException("Media asset not found: " + mediaId));
        if (!asset.getOwnerType().equals(ownerType) || !asset.getOwnerId().equals(ownerId)) {
            throw new ForbiddenException("This media asset does not belong to the given owner");
        }
        mediaStorage.delete(asset.getStorageKey());
        mediaAssetRepository.delete(asset);
    }

    private static String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }
}
