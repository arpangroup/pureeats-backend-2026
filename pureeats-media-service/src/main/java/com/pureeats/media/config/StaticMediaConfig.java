package com.pureeats.media.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Serves uploaded files back over HTTP when storage is local disk. Irrelevant once a real S3/CDN
 * provider is swapped in (those URLs are already public/pre-signed), which is why this is gated
 * behind the same {@code pureeats.media.provider=local} switch as {@link com.pureeats.media.storage.LocalDiskMediaStorage}.
 */
@Configuration
@ConditionalOnProperty(name = "pureeats.media.provider", havingValue = "local", matchIfMissing = true)
public class StaticMediaConfig implements WebMvcConfigurer {

    @Value("${pureeats.media.local.base-dir:./uploads}")
    private String baseDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = baseDir.endsWith("/") ? baseDir : baseDir + "/";
        registry.addResourceHandler("/media/**").addResourceLocations("file:" + location);
    }
}
