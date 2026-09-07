package com.pureeats.geo.index;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GeoHashTest {

    @Test
    void matchesTheWellKnownReferenceExample() {
        // The standard geohash.org worked example: (57.64911, 10.40744) -> "u4pruydqqvj".
        assertEquals("u4pruydqqvj", GeoHash.encode(57.64911, 10.40744, 11));
    }

    @Test
    void nearbyPointsShareALongerPrefixThanFarPoints() {
        String hyderabadDowntown = GeoHash.encode(17.385, 78.486, 8);
        String hyderabadNearby = GeoHash.encode(17.386, 78.487, 8);
        String delhi = GeoHash.encode(28.6139, 77.2090, 8);

        int nearbySharedPrefix = sharedPrefixLength(hyderabadDowntown, hyderabadNearby);
        int farSharedPrefix = sharedPrefixLength(hyderabadDowntown, delhi);

        org.junit.jupiter.api.Assertions.assertTrue(nearbySharedPrefix > farSharedPrefix);
    }

    private static int sharedPrefixLength(String a, String b) {
        int i = 0;
        while (i < a.length() && i < b.length() && a.charAt(i) == b.charAt(i)) {
            i++;
        }
        return i;
    }
}
