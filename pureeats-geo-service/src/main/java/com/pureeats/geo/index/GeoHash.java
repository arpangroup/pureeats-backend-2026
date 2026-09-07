package com.pureeats.geo.index;

/**
 * Standard base32 geohash encoding - a real, correct implementation (not a placeholder), since the
 * algorithm itself is pure math with no external dependency or persistence to fake. Encodes a
 * lat/lng into a short string prefix such that nearby points tend to share a prefix, letting
 * {@code PolygonBoundaryService} pre-filter candidate boundaries by simple string-prefix match
 * before running the more expensive KD-Tree search and exact point-in-polygon test.
 * <p>
 * Precision guide (roughly, at the equator): 5 chars ~ 4.9km x 4.9km cell, 6 chars ~ 1.2km x 0.6km,
 * 7 chars ~ 153m x 153m. {@code PolygonBoundaryService} defaults to 6.
 */
public final class GeoHash {

    private static final String BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz";

    private GeoHash() {
    }

    public static String encode(double lat, double lng, int precision) {
        double[] latRange = {-90.0, 90.0};
        double[] lngRange = {-180.0, 180.0};
        StringBuilder hash = new StringBuilder();
        boolean evenBit = true;
        int bit = 0;
        int ch = 0;

        while (hash.length() < precision) {
            if (evenBit) {
                double mid = (lngRange[0] + lngRange[1]) / 2;
                if (lng >= mid) {
                    ch |= (1 << (4 - bit));
                    lngRange[0] = mid;
                } else {
                    lngRange[1] = mid;
                }
            } else {
                double mid = (latRange[0] + latRange[1]) / 2;
                if (lat >= mid) {
                    ch |= (1 << (4 - bit));
                    latRange[0] = mid;
                } else {
                    latRange[1] = mid;
                }
            }
            evenBit = !evenBit;
            if (bit < 4) {
                bit++;
            } else {
                hash.append(BASE32.charAt(ch));
                bit = 0;
                ch = 0;
            }
        }
        return hash.toString();
    }
}
