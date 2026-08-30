package com.pureeats.media.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "pureeats.media.provider", havingValue = "local", matchIfMissing = true)
public class LocalDiskMediaUrlResolver implements MediaUrlResolver {

    @Value("${pureeats.media.public-base-url:http://localhost:8081}")
    private String publicBaseUrl;

    @Override
    public String resolve(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return null;
        }
        // A handful of images (saved before certain upload flows went through MediaAssetService)
        // have an already-resolvable value - a full URL or an inline data: URI - stored directly in
        // the image column. Concatenating those with the media base path would produce garbage
        // ("http://host/media/data:image/png;base64,..."), so pass them through unchanged instead
        // of treating every value as a bare storage key.
        if (storageKey.startsWith("http://") || storageKey.startsWith("https://") || storageKey.startsWith("data:")) {
            return storageKey;
        }
        return publicBaseUrl + "/media/" + storageKey;
    }
}
