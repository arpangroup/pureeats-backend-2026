package com.pureeats.geo.polygon;

import com.pureeats.geo.LatLng;

import java.util.List;

/**
 * Even-odd ray-casting point-in-polygon test - a real, correct implementation (not a placeholder),
 * same reasoning as {@link com.pureeats.geo.index.GeoHash}: this is pure math with no external
 * dependency or persistence to fake, so there's no reason to stub it out. Deliberately hand-rolled
 * instead of pulling in a spatial library (e.g. JTS) - this module stays dependency-light until a
 * real persisted implementation actually needs one.
 */
public final class PointInPolygon {

    private PointInPolygon() {
    }

    /** {@code vertices} need not be explicitly closed (first == last) - the algorithm wraps around regardless. */
    public static boolean contains(List<LatLng> vertices, double lat, double lng) {
        boolean inside = false;
        int n = vertices.size();
        if (n < 3) {
            return false;
        }
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double latI = Double.parseDouble(vertices.get(i).lat());
            double lngI = Double.parseDouble(vertices.get(i).lng());
            double latJ = Double.parseDouble(vertices.get(j).lat());
            double lngJ = Double.parseDouble(vertices.get(j).lng());

            boolean edgeCrossesRay = (lngI > lng) != (lngJ > lng);
            if (edgeCrossesRay) {
                double latAtCrossing = (latJ - latI) * (lng - lngI) / (lngJ - lngI) + latI;
                if (lat < latAtCrossing) {
                    inside = !inside;
                }
            }
        }
        return inside;
    }
}
