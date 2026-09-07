package com.pureeats.geo.polygon;

import com.pureeats.geo.LatLng;

import java.util.List;

/**
 * A drawn delivery-boundary polygon for one owner. Kept as a plain in-memory value type here - a
 * real, persisted version of this (backed by PostGIS, storing {@code vertices} as WKT/GeoJSON and
 * parsing on load) would live wherever the owner entity itself lives (e.g. {@code pureeats-catalog-service}
 * for restaurants), the same way {@code DistanceCalculator} stays generic while its callers own the
 * domain entities that use it.
 * <p>
 * {@code vertices} must form a closed ring (first point equal to the last) once real polygon input
 * arrives from a map-drawing frontend - {@link com.pureeats.geo.index.KDTree}/{@link PointInPolygon}
 * here don't enforce that themselves, matching how {@code DistanceCalculator} never validates its
 * inputs either, just returns a sane result either way.
 */
public record GeoBoundary(Long id, OwnerType ownerType, Long ownerId, List<LatLng> vertices, List<String> geoHashPrefixes) {
}
