package com.pureeats.catalog.service;

/** Compares dotted version strings ("1.2.3") component-by-component as integers - no external semver library needed for something this small. */
final class SemverComparator {

    private SemverComparator() {
    }

    /** Negative if {@code a < b}, zero if equal, positive if {@code a > b}. Malformed/non-numeric parts compare as 0. */
    static int compare(String a, String b) {
        int[] partsA = parse(a);
        int[] partsB = parse(b);
        int length = Math.max(partsA.length, partsB.length);
        for (int i = 0; i < length; i++) {
            int diff = at(partsA, i) - at(partsB, i);
            if (diff != 0) return diff;
        }
        return 0;
    }

    private static int at(int[] parts, int index) {
        return index < parts.length ? parts[index] : 0;
    }

    private static int[] parse(String version) {
        if (version == null || version.isBlank()) return new int[0];
        String[] segments = version.trim().split("\\.");
        int[] parts = new int[segments.length];
        for (int i = 0; i < segments.length; i++) {
            try {
                parts[i] = Integer.parseInt(segments[i].replaceAll("[^0-9].*$", ""));
            } catch (NumberFormatException e) {
                parts[i] = 0;
            }
        }
        return parts;
    }
}
