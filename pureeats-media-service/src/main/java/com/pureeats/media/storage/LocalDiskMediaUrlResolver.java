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
        return publicBaseUrl + "/media/" + storageKey;
    }
}
