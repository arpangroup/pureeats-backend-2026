package com.pureeats.geo.distance;

import com.pureeats.geo.LatLng;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class SwitchableDistanceCalculatorTest {

    /** The example from the request: straight line 3.06 km (Google's road distance is 6.7 km). */
    private static final String R_LAT = "22.635895", R_LNG = "88.353208", C_LAT = "22.6522185", C_LNG = "88.3772049";

    private static DistanceSettings settings(String method, String key) {
        return new DistanceSettings() {
            @Override
            public String method() {
                return method;
            }

            @Override
            public String googleApiKey() {
                return key;
            }
        };
    }

    @Test
    void straightLine_isTheDefault() {
        var calc = new SwitchableDistanceCalculator(() -> settings(DistanceSettings.STRAIGHT_LINE, ""), new HaversineDistanceCalculator(), 1000);
        assertEquals(new BigDecimal("3.06"), calc.distanceKm(R_LAT, R_LNG, C_LAT, C_LNG));
        assertInstanceOf(HaversineDistanceCalculator.class, calc.active());
    }

    @Test
    void switchingMethod_appliesOnTheNextCall_andGoogleWithoutAKeyFallsBackToStraightLine() {
        AtomicReference<DistanceSettings> current = new AtomicReference<>(settings(DistanceSettings.STRAIGHT_LINE, ""));
        var calc = new SwitchableDistanceCalculator(current::get, new HaversineDistanceCalculator(), 1000);
        assertInstanceOf(HaversineDistanceCalculator.class, calc.active());

        current.set(settings(DistanceSettings.GOOGLE, ""));
        assertInstanceOf(GoogleDistanceMatrixCalculator.class, calc.active());
        // No key: never calls Google, never throws - the straight line is used.
        assertEquals(new BigDecimal("3.06"), calc.distanceKm(R_LAT, R_LNG, C_LAT, C_LNG));
        List<TravelEstimate> batch = calc.estimatesTo(List.of(new LatLng(R_LAT, R_LNG), new LatLng("bad", "x")), new LatLng(C_LAT, C_LNG));
        assertEquals(new BigDecimal("3.06"), batch.get(0).distanceKm());
        assertEquals(BigDecimal.ZERO, batch.get(1).distanceKm(), "unusable coordinates stay zero, as before");
    }

    @Test
    void straightLineKm_isAlwaysTheFreeFigure_evenUnderGoogle() {
        var calc = new SwitchableDistanceCalculator(() -> settings(DistanceSettings.GOOGLE, "some-key"), new HaversineDistanceCalculator(), 1000);
        assertEquals(new BigDecimal("3.06"), calc.straightLineKm(R_LAT, R_LNG, C_LAT, C_LNG));
    }
}
