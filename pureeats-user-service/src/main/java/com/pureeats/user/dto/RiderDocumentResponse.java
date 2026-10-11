package com.pureeats.user.dto;

import java.time.LocalDateTime;

/** One uploaded partner document (the latest upload of its type). {@code contentType} tells a photo from a PDF. */
public record RiderDocumentResponse(String type, String label, String url, String contentType, LocalDateTime uploadedAt) {
}
