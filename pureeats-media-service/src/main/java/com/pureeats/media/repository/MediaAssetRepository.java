package com.pureeats.media.repository;

import com.pureeats.media.entity.MediaAsset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, Long> {
    List<MediaAsset> findByOwnerTypeAndOwnerIdOrderByCreatedAtDesc(String ownerType, Long ownerId);

    long countByOwnerTypeAndOwnerId(String ownerType, Long ownerId);
}
