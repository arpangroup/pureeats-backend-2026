package com.pureeats.app.geodemo.controller;

import com.pureeats.app.geodemo.dto.GeoSimulatorRestaurant;
import com.pureeats.app.geodemo.dto.NearbyRestaurantResult;
import com.pureeats.geo.distance.DistanceMatrixResult;
import com.pureeats.geo.distance.HaversineDistanceCalculator;
import com.pureeats.geo.routing.Route;
import com.pureeats.geo.routing.StraightLineRoutingEngine;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plain unit test (no Spring context, no database) for the demo controller behind
 * geo-delivery-simulator's "live backend" toggle - wires the real {@link HaversineDistanceCalculator}
 * and {@link StraightLineRoutingEngine} directly, the same beans {@code DistanceCalculatorConfig}/
 * {@code RoutingEngineConfig} would produce by default.
 */
class GeoSimulatorControllerTest {

    private final GeoSimulatorController controller =
            new GeoSimulatorController(new HaversineDistanceCalculator(),
                    new StraightLineRoutingEngine(new HaversineDistanceCalculator()));

    @Test
    void restaurantsReturnsTheFixedDummyList() {
        List<GeoSimulatorRestaurant> restaurants = controller.restaurants().getData();

        assertThat(restaurants).hasSize(6);
        assertThat(restaurants).extracting(GeoSimulatorRestaurant::name).contains("HiTech City Grill");
    }

    @Test
    void nearbyRanksByAscendingDistanceAndRespectsLimit() {
        List<NearbyRestaurantResult> nearest = controller.nearby("17.385", "78.486", 3).getData();

        assertThat(nearest).hasSize(3);
        for (int i = 1; i < nearest.size(); i++) {
            assertThat(nearest.get(i).distanceKm()).isGreaterThanOrEqualTo(nearest.get(i - 1).distanceKm());
        }
        assertThat(nearest.get(0).restaurant().name()).isEqualTo("Hyderabad Downtown Biryani House");
        assertThat(nearest.get(0).distanceKm()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(nearest.get(0).etaMinutes()).isZero();
    }

    @Test
    void distanceMatrixCoversEveryDummyRestaurant() {
        DistanceMatrixResult matrix = controller.distanceMatrix("17.385", "78.486").getData();

        assertThat(matrix.origins()).hasSize(1);
        assertThat(matrix.destinations()).hasSize(6);
        assertThat(matrix.distancesKm()).hasDimensions(1, 6);
    }

    @Test
    void routeReturnsAPositiveDistanceAndEtaWithNoPolyline() {
        Route route = controller.route("17.385", "78.486", "17.446", "78.382").getData();

        assertThat(route.distanceKm()).isGreaterThan(BigDecimal.ZERO);
        assertThat(route.etaMinutes()).isGreaterThan(0);
        assertThat(route.polyline()).isNull();
    }
}
