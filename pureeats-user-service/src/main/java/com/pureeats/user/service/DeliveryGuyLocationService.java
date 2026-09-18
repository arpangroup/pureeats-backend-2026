package com.pureeats.user.service;

import com.pureeats.domain.common.exception.ResourceNotFoundException;
import com.pureeats.domain.entity.DeliveryGuyDetail;
import com.pureeats.user.repository.DeliveryGuyDetailRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * The single place that writes a {@link DeliveryGuyDetail}'s {@code lastLat}/{@code lastLng}/
 * {@code lastSeenAt} - shared by both the rider's own self-service ping (order-service's
 * {@code DeliveryOrderController}, principal-scoped) and the admin override
 * ({@code AdminDeliveryGuyController} in pureeats-app, id-scoped), so the write itself lives in one
 * place instead of being duplicated per caller. Lives here (pureeats-user-service, alongside
 * {@link DeliveryGuyDetailRepository}) rather than in order-service, since pureeats-app depends on
 * both user-service and order-service but user-service can't depend back on order-service (see
 * {@code AdminDeliveryGuyService}'s javadoc for the same constraint).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryGuyLocationService {

    private final DeliveryGuyDetailRepository deliveryGuyDetailRepository;

    @Transactional
    public void updateLocation(Long deliveryGuyDetailId, BigDecimal lat, BigDecimal lng) {
        DeliveryGuyDetail detail = deliveryGuyDetailRepository.findById(deliveryGuyDetailId)
                .orElseThrow(() -> {
                    log.warn("Location update rejected: delivery partner {} not found", deliveryGuyDetailId);
                    return new ResourceNotFoundException("Delivery partner not found: " + deliveryGuyDetailId);
                });
        detail.setLastLat(lat);
        detail.setLastLng(lng);
        detail.setLastSeenAt(LocalDateTime.now());
        deliveryGuyDetailRepository.save(detail);
        log.debug("Updated location for delivery partner {}: ({}, {})", deliveryGuyDetailId, lat, lng);
    }
}
