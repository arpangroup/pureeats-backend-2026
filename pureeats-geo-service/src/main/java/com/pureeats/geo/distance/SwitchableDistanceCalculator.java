package com.pureeats.geo.distance;

import com.pureeats.geo.LatLng;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Supplier;

/**
 * The application's one {@link DistanceCalculator}: delegates every call to whichever method
 * {@link DistanceSettings#method()} says is active right now - straight-line (haversine, the default) or the
 * Google Distance Matrix - so switching it in admin Settings takes effect on the next request, no restart.
 */
public class SwitchableDistanceCalculator implements DistanceCalculator {

    private final Supplier<DistanceSettings> settings;
    private final DistanceCalculator straightLine;
    private final GoogleDistanceMatrixCalculator google;

    public SwitchableDistanceCalculator(Supplier<DistanceSettings> settings, DistanceCalculator straightLine, int googleTimeoutMs) {
        this.settings = settings;
        this.straightLine = straightLine;
        this.google = new GoogleDistanceMatrixCalculator(() -> settings.get().googleApiKey(), googleTimeoutMs);
    }

    /** The calculator in effect for this call. */
    DistanceCalculator active() {
        return DistanceSettings.GOOGLE.equalsIgnoreCase(settings.get().method()) ? google : straightLine;
    }

    @Override
    public BigDecimal distanceKm(String lat1, String lng1, String lat2, String lng2) {
        return active().distanceKm(lat1, lng1, lat2, lng2);
    }

    @Override
    public int etaMinutes(String lat1, String lng1, String lat2, String lng2) {
        return active().etaMinutes(lat1, lng1, lat2, lng2);
    }

    @Override
    public TravelEstimate estimate(String lat1, String lng1, String lat2, String lng2) {
        return active().estimate(lat1, lng1, lat2, lng2);
    }

    @Override
    public List<TravelEstimate> estimatesTo(List<LatLng> origins, LatLng destination) {
        return active().estimatesTo(origins, destination);
    }

    @Override
    public BigDecimal straightLineKm(String lat1, String lng1, String lat2, String lng2) {
        return straightLine.distanceKm(lat1, lng1, lat2, lng2);
    }
}
