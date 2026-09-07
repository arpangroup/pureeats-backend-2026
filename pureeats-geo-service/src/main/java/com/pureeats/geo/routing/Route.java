package com.pureeats.geo.routing;

import java.math.BigDecimal;

/** {@code polyline} is an encoded path (e.g. Google's polyline format) for map display - {@code null} from an implementation that only has a distance/ETA estimate, not an actual traced route. */
public record Route(BigDecimal distanceKm, int etaMinutes, String polyline) {
}
