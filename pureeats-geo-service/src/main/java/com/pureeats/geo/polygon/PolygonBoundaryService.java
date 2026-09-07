package com.pureeats.geo.polygon;

import java.util.List;

/**
 * The actual point-in-polygon capability, generic over {@link OwnerType} - a restaurant, a
 * warehouse, or a future vendor is checked exactly the same way. This is the interface consumers
 * (e.g. a future {@code DeliveryPolygonRule} in {@code pureeats-order-service}, or
 * {@code RestaurantService.checkDeliveryArea}) would depend on; it says nothing about how
 * boundaries are stored, so a real PostGIS-backed implementation can replace
 * {@link InMemoryPolygonBoundaryService} later without any caller changing.
 * <p>
 * Only registered as a bean at all when {@code pureeats.geo.polygon-boundary.enabled=true} (see
 * {@link PolygonBoundaryServiceConfig}) - a consumer that wants this to be a genuinely optional,
 * conditionally-applied rule should depend on {@code Optional<PolygonBoundaryService>} (or be
 * {@code @ConditionalOnBean}-gated itself), not assume the bean always exists.
 */
public interface PolygonBoundaryService {

    /** Does the given owner's delivery boundary contain this point? {@code false} (not an exception) if the owner has no registered boundary at all. */
    boolean containsPoint(OwnerType ownerType, Long ownerId, String lat, String lng);

    /** Every owner of this type whose boundary contains the point - the "which stores can deliver here" query. */
    List<Long> ownersContaining(OwnerType ownerType, String lat, String lng);
}
