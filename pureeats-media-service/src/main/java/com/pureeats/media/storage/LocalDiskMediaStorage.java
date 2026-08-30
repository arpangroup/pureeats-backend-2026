package com.pureeats.media.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
@ConditionalOnProperty(name = "pureeats.media.provider", havingValue = "local", matchIfMissing = true)
public class LocalDiskMediaStorage implements MediaStorage {

    @Value("${pureeats.media.local.base-dir:./uploads}")
    private String baseDir;

    @Override
    public String store(MultipartFile file, String storageKey) throws IOException {
        Path target = Path.of(baseDir).resolve(storageKey).normalize();
        if (!target.startsWith(Path.of(baseDir).normalize())) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        Files.createDirectories(target.getParent());
        file.transferTo(target);
        return storageKey;
    }

    @Override
    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(Path.of(baseDir).resolve(storageKey).normalize());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
