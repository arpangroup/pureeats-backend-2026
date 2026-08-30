package com.pureeats.media.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Persists uploaded file bytes and deletes them later. Deliberately the only seam between
 * {@link com.pureeats.media.service.MediaAssetService} and "where the bytes actually live" -
 * swapping local disk for S3 (or anything else) later means adding one new implementation of
 * this interface, selected by the {@code pureeats.media.provider} property; nothing else in
 * this module or any caller changes.
 */
public interface MediaStorage {

    /** Writes {@code file}'s bytes under {@code storageKey} and returns the same key back. */
    String store(MultipartFile file, String storageKey) throws IOException;

    void delete(String storageKey);
}
