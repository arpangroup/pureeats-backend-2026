package com.pureeats.media.storage;

/**
 * Turns a stored key (as saved on e.g. {@code Restaurant.image}, {@code User.photo}) into a
 * fully-qualified, servable URL. Every response DTO that exposes an image field should resolve
 * it through this, never return the raw stored key - that's what makes swapping local-disk URLs
 * for S3/CDN URLs later a change in exactly one class, not every response mapper.
 */
public interface MediaUrlResolver {

    /** @return the resolved URL, or {@code null} if {@code storageKey} is null/blank. */
    String resolve(String storageKey);
}
