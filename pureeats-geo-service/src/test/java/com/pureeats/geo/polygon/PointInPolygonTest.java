package com.pureeats.geo.polygon;

import com.pureeats.geo.LatLng;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PointInPolygonTest {

    // A simple square: (17.38,78.48) -> (17.40,78.48) -> (17.40,78.50) -> (17.38,78.50).
    private final List<LatLng> square = List.of(
            new LatLng("17.38", "78.48"),
            new LatLng("17.40", "78.48"),
            new LatLng("17.40", "78.50"),
            new LatLng("17.38", "78.50")
    );

    @Test
    void pointWellInsideTheSquareIsContained() {
        assertTrue(PointInPolygon.contains(square, 17.39, 78.49));
    }

    @Test
    void pointWellOutsideTheSquareIsNotContained() {
        assertFalse(PointInPolygon.contains(square, 17.50, 78.60));
    }

    @Test
    void degenerateLessThanThreeVerticesIsNeverContained() {
        assertFalse(PointInPolygon.contains(List.of(new LatLng("17.38", "78.48"), new LatLng("17.40", "78.48")), 17.39, 78.48));
    }
}
