package com.pureeats.geo.index;

import com.pureeats.geo.LatLng;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KDTreeTest {

    @Test
    void nearestFindsTheClosestPointNotJustTheFirstInsertionOrder() {
        List<LatLng> points = List.of(
                new LatLng("17.500", "78.500"), // far
                new LatLng("17.386", "78.487"), // closest to the query point below
                new LatLng("28.613", "77.209")  // far (Delhi)
        );
        KDTree<LatLng> tree = new KDTree<>(points, p -> p);

        var nearest = tree.nearest(17.385, 78.486);

        assertTrue(nearest.isPresent());
        assertEquals("17.386", nearest.get().lat());
        assertEquals("78.487", nearest.get().lng());
    }

    @Test
    void kNearestReturnsClosestFirstAndRespectsTheLimit() {
        List<LatLng> points = List.of(
                new LatLng("17.390", "78.490"),
                new LatLng("17.386", "78.487"),
                new LatLng("17.400", "78.500"),
                new LatLng("28.613", "77.209")
        );
        KDTree<LatLng> tree = new KDTree<>(points, p -> p);

        List<LatLng> nearest2 = tree.kNearest(17.385, 78.486, 2);

        assertEquals(2, nearest2.size());
        assertEquals("17.386", nearest2.get(0).lat());
        assertEquals("17.390", nearest2.get(1).lat());
    }
}
