package com.pureeats.geo.polygon;

import com.pureeats.geo.LatLng;
import com.pureeats.geo.index.GeoHash;
import com.pureeats.geo.index.KDTree;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The "dummy" implementation referenced everywhere else - not persisted (no PostGIS, no table;
 * {@link #register} just holds boundaries in a concurrent map), but the query algorithm itself is
 * real: the exact three-stage pipeline described in {@code architecture-map.html}'s "user search
 * flow" - GeoHash prefilter, then KD-Tree nearest-neighbor narrowing, then exact point-in-polygon
 * validation. Swapping this for a PostGIS-backed implementation later changes nothing about
 * {@link PolygonBoundaryService}'s contract or any of its callers.
 */
public class InMemoryPolygonBoundaryService implements PolygonBoundaryService {

    private static final int GEOHASH_PRECISION = 6;
    private static final int KD_TREE_CANDIDATE_LIMIT = 20;

    private final Map<OwnerType, List<GeoBoundary>> boundariesByType = new ConcurrentHashMap<>();

    /** Test/demo-data seeding only - not part of {@link PolygonBoundaryService}, since a real persisted implementation wouldn't have a "register" method, just queries against its own table. */
    public void register(GeoBoundary boundary) {
        boundariesByType.computeIfAbsent(boundary.ownerType(), t -> new ArrayList<>()).add(boundary);
    }

    @Override
    public boolean containsPoint(OwnerType ownerType, Long ownerId, String lat, String lng) {
        double latD = Double.parseDouble(lat);
        double lngD = Double.parseDouble(lng);
        return boundariesByType.getOrDefault(ownerType, List.of()).stream()
                .filter(b -> b.ownerId().equals(ownerId))
                .anyMatch(b -> PointInPolygon.contains(b.vertices(), latD, lngD));
    }

    @Override
    public List<Long> ownersContaining(OwnerType ownerType, String lat, String lng) {
        double latD = Double.parseDouble(lat);
        double lngD = Double.parseDouble(lng);
        List<GeoBoundary> all = boundariesByType.getOrDefault(ownerType, List.of());
        if (all.isEmpty()) {
            return List.of();
        }

        // Stage 1: GeoHash prefilter - keep boundaries sharing a geohash prefix with the query point.
        String queryHash = GeoHash.encode(latD, lngD, GEOHASH_PRECISION);
        List<GeoBoundary> hashFiltered = all.stream()
                .filter(b -> b.geoHashPrefixes().stream().anyMatch(queryHash::startsWith))
                .toList();
        // Sparse dummy data may miss an exact prefix match - fall back to the full set rather than
        // silently returning nothing; a real, densely-indexed implementation wouldn't need this.
        List<GeoBoundary> candidates = hashFiltered.isEmpty() ? all : hashFiltered;

        // Stage 2: KD-Tree narrows to the nearest boundary centroids before the exact (more
        // expensive) geometry test runs on anything.
        KDTree<GeoBoundary> index = new KDTree<>(candidates, InMemoryPolygonBoundaryService::centroidOf);
        List<GeoBoundary> nearest = index.kNearest(latD, lngD, Math.min(KD_TREE_CANDIDATE_LIMIT, candidates.size()));

        // Stage 3: exact point-in-polygon validation - only this step decides "deliverable or not."
        return nearest.stream()
                .filter(b -> PointInPolygon.contains(b.vertices(), latD, lngD))
                .map(GeoBoundary::ownerId)
                .distinct()
                .toList();
    }

    private static LatLng centroidOf(GeoBoundary boundary) {
        double latSum = 0;
        double lngSum = 0;
        for (LatLng vertex : boundary.vertices()) {
            latSum += Double.parseDouble(vertex.lat());
            lngSum += Double.parseDouble(vertex.lng());
        }
        int n = boundary.vertices().size();
        return new LatLng(String.valueOf(latSum / n), String.valueOf(lngSum / n));
    }
}
