package com.pureeats.geo.distance;

import com.pureeats.geo.LatLng;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DistanceCalculatorDefaultMethodsTest {

    private final DistanceCalculator calculator = new HaversineDistanceCalculator();

    private record Rider(String name, LatLng at) {
    }

    @Test
    void isWithinRadiusMatchesDistanceKmDirectly() {
        BigDecimal distance = calculator.distanceKm("17.385", "78.486", "17.500", "78.600");

        assertTrue(calculator.isWithinRadius("17.385", "78.486", "17.500", "78.600", distance.add(BigDecimal.ONE)));
        assertFalse(calculator.isWithinRadius("17.385", "78.486", "17.500", "78.600", distance.subtract(BigDecimal.ONE)));
    }

    @Test
    void topNNearestRanksByDistanceAscending() {
        List<Rider> riders = List.of(
                new Rider("far", new LatLng("28.613", "77.209")),
                new Rider("near", new LatLng("17.386", "78.487")),
                new Rider("medium", new LatLng("17.500", "78.600"))
        );

        List<Ranked<Rider>> top2 = calculator.topNNearest("17.385", "78.486", riders, 2, Rider::at);

        assertEquals(2, top2.size());
        assertEquals("near", top2.get(0).item().name());
        assertEquals("medium", top2.get(1).item().name());
    }

    @Test
    void nearestAvailableRiderIsTheSingleClosestCandidate() {
        List<Rider> riders = List.of(
                new Rider("far", new LatLng("28.613", "77.209")),
                new Rider("near", new LatLng("17.386", "78.487"))
        );

        Optional<Rider> nearest = calculator.nearestAvailableRider("17.385", "78.486", riders, Rider::at);

        assertTrue(nearest.isPresent());
        assertEquals("near", nearest.get().name());
    }

    @Test
    void distanceMatrixMatchesPairwiseDistanceKmCalls() {
        List<LatLng> origins = List.of(new LatLng("17.385", "78.486"));
        List<LatLng> destinations = List.of(new LatLng("17.500", "78.600"), new LatLng("28.613", "77.209"));

        DistanceMatrixResult matrix = calculator.distanceMatrix(origins, destinations);

        assertEquals(calculator.distanceKm("17.385", "78.486", "17.500", "78.600"), matrix.distancesKm()[0][0]);
        assertEquals(calculator.distanceKm("17.385", "78.486", "28.613", "77.209"), matrix.distancesKm()[0][1]);
    }

    @Test
    void etaMinutesIsPositiveAndGrowsWithDistance() {
        int shortEta = calculator.etaMinutes("17.385", "78.486", "17.386", "78.487");
        int longEta = calculator.etaMinutes("17.385", "78.486", "28.613", "77.209");

        assertTrue(shortEta > 0);
        assertTrue(longEta > shortEta);
    }

    @Test
    void boundingBoxSurroundsTheCenterPoint() {
        BoundingBox box = calculator.boundingBox("17.385", "78.486", BigDecimal.TEN);

        double minLat = Double.parseDouble(box.minLat());
        double maxLat = Double.parseDouble(box.maxLat());
        double minLng = Double.parseDouble(box.minLng());
        double maxLng = Double.parseDouble(box.maxLng());

        assertTrue(minLat < 17.385 && 17.385 < maxLat);
        assertTrue(minLng < 78.486 && 78.486 < maxLng);
    }

    @Test
    void methodsNeedingExternalInfrastructureThrowUnsupportedOperationException() {
        assertThrows(UnsupportedOperationException.class, () -> calculator.nearby("17.385", "78.486", BigDecimal.TEN, 5));
        assertThrows(UnsupportedOperationException.class, () -> calculator.geocode("some address"));
        assertThrows(UnsupportedOperationException.class, () -> calculator.reverseGeocode("17.385", "78.486"));
    }
}
