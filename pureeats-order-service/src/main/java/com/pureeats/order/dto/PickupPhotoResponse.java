package com.pureeats.order.dto;

import java.time.LocalDateTime;

/** A photo of the packed order taken by the delivery partner at pickup. */
public record PickupPhotoResponse(Long id, String url, LocalDateTime takenAt) {
}
