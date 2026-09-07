package com.pureeats.geo.polygon;

import com.pureeats.geo.LatLng;
import com.pureeats.geo.index.GeoHash;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryPolygonBoundaryServiceTest {

    private final InMemoryPolygonBoundaryService service = new InMemoryPolygonBoundaryService();

    // Restaurant 1's delivery zone: a square roughly covering Hyderabad downtown.
    private final List<LatLng> zone1 = List.of(
            new LatLng("17.38", "78.48"),
            new LatLng("17.40", "78.48"),
            new LatLng("17.40", "78.50"),
            new LatLng("17.38", "78.50")
    );

    @BeforeEach
    void seedOneRestaurantBoundary() {
        String prefix = GeoHash.encode(17.39, 78.49, 5);
        service.register(new GeoBoundary(1L, OwnerType.RESTAURANT, 100L, zone1, List.of(prefix)));
    }

    @Test
    void containsPointIsTrueInsideTheRegisteredBoundary() {
        assertTrue(service.containsPoint(OwnerType.RESTAURANT, 100L, "17.39", "78.49"));
    }

    @Test
    void containsPointIsFalseOutsideTheRegisteredBoundary() {
        assertFalse(service.containsPoint(OwnerType.RESTAURANT, 100L, "17.50", "78.60"));
    }

    @Test
    void containsPointIsFalseForAnUnknownOwnerId() {
        assertFalse(service.containsPoint(OwnerType.RESTAURANT, 999L, "17.39", "78.49"));
    }

    @Test
    void ownersContainingReturnsTheRegisteredOwnerForAPointInsideItsZone() {
        List<Long> owners = service.ownersContaining(OwnerType.RESTAURANT, "17.39", "78.49");

        assertEquals(List.of(100L), owners);
    }

    @Test
    void ownersContainingReturnsNothingForAPointOutsideEveryZone() {
        List<Long> owners = service.ownersContaining(OwnerType.RESTAURANT, "17.50", "78.60");

        assertEquals(List.of(), owners);
    }
}
