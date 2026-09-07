package com.pureeats.geo.distance;

import com.pureeats.geo.LatLng;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Straight-line distance between two lat/lng points, in kilometers. This is the single
 * abstraction {@code OrderPricingService} (delivery-charge tiers), {@code RestaurantService}
 * (delivery-area check + nearby-restaurant search) and the cart-validation pipeline's
 * {@code DeliveryRadiusRule} all consume - before this existed, order-service and catalog-service
 * each had their own copy-pasted haversine implementation.
 * <p>
 * Swapping the algorithm (or calling out to a paid API like Google's Distance Matrix, which
 * accounts for real road routing instead of a straight line) is purely a matter of which
 * implementation is {@code @ConditionalOnProperty}-selected in {@link DistanceCalculatorConfig} -
 * no caller of this interface ever changes.
 * <p>
 * The {@code default} methods below are a fuller location-database-style API layered on top of the
 * one abstract method every implementation must still provide. None of the three current
 * implementations ({@link HaversineDistanceCalculator}, {@link EuclideanDistanceCalculator},
 * {@link GoogleDistanceMatrixCalculator}) override any of them - some ({@link #isWithinRadius},
 * {@link #boundingBox}, {@link #topNNearest}, {@link #distanceMatrix}, {@link #nearestAvailableRider})
 * have a genuine, correct default built purely from {@link #distanceKm}; others
 * ({@link #nearby}, {@link #geocode}, {@link #reverseGeocode}) need infrastructure this interface
 * alone can't supply (a candidate data source, an external geocoding provider) and default to
 * throwing {@link UnsupportedOperationException} until a real implementation overrides them - see
 * {@code pureeats-geo-service/docs/architecture-map.html}.
 */
public interface DistanceCalculator {

    /** Null/unparseable coordinates on either end yield {@link BigDecimal#ZERO} - every implementation must honor this, never throw. */
    BigDecimal distanceKm(String lat1, String lng1, String lat2, String lng2);

    /** Cheap yes/no gate when the exact km value isn't needed - a real default, no override required. */
    default boolean isWithinRadius(String centerLat, String centerLng, String pointLat, String pointLng, BigDecimal radiusKm) {
        return distanceKm(centerLat, centerLng, pointLat, pointLng).compareTo(radiusKm) <= 0;
    }

    /**
     * The same flat degree-per-km approximation {@code RestaurantService.findNearby} already
     * inlines as a bounding-box pre-filter before running the real distance calculation - exposed
     * here so every caller shares one implementation instead of copy-pasting the math.
     */
    default BoundingBox boundingBox(String centerLat, String centerLng, BigDecimal radiusKm) {
        double lat = Double.parseDouble(centerLat);
        double lng = Double.parseDouble(centerLng);
        double kmPerDegreeLat = 111.32;
        double kmPerDegreeLng = kmPerDegreeLat * Math.max(Math.cos(Math.toRadians(lat)), 0.1);
        double latDelta = radiusKm.doubleValue() / kmPerDegreeLat;
        double lngDelta = radiusKm.doubleValue() / kmPerDegreeLng;
        return new BoundingBox(
                String.valueOf(lat - latDelta), String.valueOf(lng - lngDelta),
                String.valueOf(lat + latDelta), String.valueOf(lng + lngDelta));
    }

    /**
     * Ranks a caller-supplied candidate list by distance from one origin - deliberately generic
     * (candidates come from the caller, not a repository this module doesn't have) so it works for
     * riders, restaurants, or anything else with a location, without this module depending on any
     * of those domain types.
     */
    default <T> List<Ranked<T>> topNNearest(String originLat, String originLng, List<T> candidates, int n, Function<T, LatLng> locationOf) {
        List<Ranked<T>> ranked = new ArrayList<>(candidates.size());
        for (T candidate : candidates) {
            LatLng at = locationOf.apply(candidate);
            ranked.add(new Ranked<>(candidate, distanceKm(originLat, originLng, at.lat(), at.lng())));
        }
        ranked.sort(Comparator.comparing(Ranked::distanceKm));
        return ranked.size() > n ? ranked.subList(0, n) : ranked;
    }

    /** The nearest of a caller-supplied candidate list - a one-result special case of {@link #topNNearest}, kept as its own method since "which rider gets this pickup" is the single most common call this whole roadmap exists for. */
    default <T> Optional<T> nearestAvailableRider(String pickupLat, String pickupLng, List<T> candidates, Function<T, LatLng> locationOf) {
        return topNNearest(pickupLat, pickupLng, candidates, 1, locationOf).stream().findFirst().map(Ranked::item);
    }

    /**
     * Naive O(origins x destinations) fallback - correct, but makes that many {@link #distanceKm}
     * calls. A provider-backed override (e.g. Google's actual batch endpoint) should replace this
     * with one real round-trip instead of many.
     */
    default DistanceMatrixResult distanceMatrix(List<LatLng> origins, List<LatLng> destinations) {
        BigDecimal[][] distances = new BigDecimal[origins.size()][destinations.size()];
        for (int i = 0; i < origins.size(); i++) {
            LatLng origin = origins.get(i);
            for (int j = 0; j < destinations.size(); j++) {
                LatLng destination = destinations.get(j);
                distances[i][j] = distanceKm(origin.lat(), origin.lng(), destination.lat(), destination.lng());
            }
        }
        return new DistanceMatrixResult(origins, destinations, distances);
    }

    /**
     * Real travel-time estimate, not just straight-line distance. No implementation here has real
     * road-network data, so this defaults to a naive flat-average-speed guess - a
     * {@code RoutingEngine}-backed implementation (see {@code com.pureeats.geo.routing}) should
     * override this with a real estimate once one exists.
     */
    default int etaMinutes(String lat1, String lng1, String lat2, String lng2) {
        double naiveAverageSpeedKmph = 25.0;
        double km = distanceKm(lat1, lng1, lat2, lng2).doubleValue();
        return (int) Math.ceil(km / naiveAverageSpeedKmph * 60);
    }

    /** Needs a candidate data source (a repository or spatial index) this interface alone can't have - not implementable as a generic default. See {@code com.pureeats.geo.polygon.PolygonBoundaryService} / the KD-Tree index for the real search machinery once built. */
    default List<LatLng> nearby(String lat, String lng, BigDecimal radiusKm, int limit) {
        throw new UnsupportedOperationException("nearby(...) needs a candidate data source - not implemented by " + getClass().getSimpleName());
    }

    /** Needs an external geocoding provider - not implementable as a generic default. */
    default LatLng geocode(String address) {
        throw new UnsupportedOperationException("geocode(...) needs an external geocoding provider - not implemented by " + getClass().getSimpleName());
    }

    /** Needs an external geocoding provider - not implementable as a generic default. */
    default String reverseGeocode(String lat, String lng) {
        throw new UnsupportedOperationException("reverseGeocode(...) needs an external geocoding provider - not implemented by " + getClass().getSimpleName());
    }
}
