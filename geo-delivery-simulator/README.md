# Geo Delivery Simulator

A standalone, single-file web page that visually simulates the geo pipeline documented in
`pureeats-geo-service/docs/architecture-map.html` - GeoHash prefilter, nearest-candidate narrowing,
point-in-polygon validation, `topNNearest` / `distanceMatrix`, and routing - using an interactive
map so the whole thing can be understood (and demoed) without running the Java backend at all.

Deliberately **plain HTML/CSS/JavaScript, no build step, no npm, no React** - a page this small and
single-purpose doesn't get anything from a component framework, and "double-click to open" is a much
easier thing to hand someone than "clone, install, build."

## Running it

Just open `index.html` in a browser. It loads Leaflet and Leaflet.draw from a CDN for the map and
polygon-drawing tool; the core algorithms (GeoHash encode/decode, Haversine distance,
point-in-polygon, the naive ETA formula) are plain JavaScript with no dependency, ported
line-for-line from the real Java classes in `pureeats-geo-service`
(`com.pureeats.geo.index.GeoHash`, `com.pureeats.geo.distance.HaversineDistanceCalculator`,
`com.pureeats.geo.polygon.PointInPolygon`, `DistanceCalculator`'s default methods) so the simulation
matches the backend's actual behavior, not an approximation of it. Location search and road routing
call two free public APIs (Nominatim, OSRM - see below).

If your browser blocks map tile requests from a `file://` page, serve the folder instead of opening
it directly, e.g. from this directory:

```bash
python -m http.server 8080
```

then visit `http://localhost:8080`.

## What it does

1. **Draw delivery zones** - trace a polygon on the map and name it; each one is exactly a
   `GeoBoundary` (`ownerType=RESTAURANT` implied, one polygon per store). A "Load sample zones"
   button seeds three example zones matching the worked example in the docs (Hyderabad Downtown /
   HiTech City / Jubilee Hills). Their centroids also stand in as "restaurant locations" for panels
   4 and 5 below whenever the live backend is off.
2. **GeoHash grid overlay** - draws the actual GeoHash cells covering the current map view at a
   chosen precision (5/6/7), so you can see exactly what "bucket" any point on the map falls into.
3. **Simulate a user search** - click a point on the map and watch the three-stage pipeline run,
   with a step-by-step log (shown in the results panel at the bottom of the screen) and the map
   itself highlighting: the query point's GeoHash cell, lines to the nearest candidate zones
   (distance-sorted, standing in for the real KD-Tree - behaviorally identical for a handful of
   demo points), and green/red polygon highlighting for whether the exact point-in-polygon test
   says that zone actually covers the clicked point.
4. **Search bar with autosuggest** - type a place name in the header search box (OpenStreetMap's
   Nominatim API, debounced, arrow-key/Enter navigable) and pick a result to fly the map there.
5. **Nearby restaurants** - click "Pick user location", then "Pick user location" mode ranks every
   candidate restaurant by distance - exactly `DistanceCalculator.topNNearest` plus
   `distanceMatrix`; ETA uses the same naive 25 km/h `etaMinutes` default. Results (with a small
   distance-matrix table) appear in the bottom results panel.
6. **Route simulator** - pick a start and end point, then "Find routes" draws a straight-line
   reference (`DistanceCalculator.distanceKm`), the live backend's `RoutingEngine` estimate if
   connected, and every real road-route alternative from OSRM's public routing API - all drawn on
   the map, with the shortest **road route** highlighted in bold teal (straight-line/backend
   estimates are always shorter by construction, so they're labeled "reference" rather than
   competing for "shortest").
7. **Optional live backend** - the "Live backend" toggle in the header calls a small demo REST API
   (`GeoSimulatorController` in `pureeats-app`, see below) that runs the *real* Java
   `DistanceCalculator` / `RoutingEngine` beans against a small dummy restaurant list, instead of
   this page's client-side JS port of the same algorithms. Off by default, and every feature falls
   back to client-side simulation automatically if the backend is off, unreachable, or a call
   fails - nothing here *requires* the Java app to be running.

## Base map: Google Maps by default, OpenStreetMap as a one-line fallback

The base map is chosen by a single constant near the top of the `<script>` in `index.html`:

```js
const MAP_PROVIDER = 'google'; // 'google' | 'osm'
const GOOGLE_MAPS_API_KEY = ''; // paste a Google Maps JavaScript API key here to use 'google'
```

Every feature on the page (drawing, search, GeoHash grid, nearby/routing simulation) is plain
Leaflet API calls, so switching `MAP_PROVIDER` is the *only* line that ever needs to change - real
Google Maps tiles are mounted as a Leaflet base layer via the
[`Leaflet.GoogleMutant`](https://github.com/shramov/leaflet-plugins) plugin, so nothing else in the
file is aware of which provider is active.

`GOOGLE_MAPS_API_KEY` is intentionally left blank - a real key is tied to your own Google Cloud
billing account, so this page can't ship with one. With `MAP_PROVIDER='google'` and no key set, the
page detects that at load time, shows a dismissible on-page notice, logs a console warning, and
falls back to OpenStreetMap tiles automatically rather than breaking.

## The optional live backend

`pureeats-app`'s `GeoSimulatorController` (`/api/v1/geo/simulator/*`, public GET routes, no auth)
exposes four endpoints backed by the real `DistanceCalculator`/`RoutingEngine` Spring beans and a
fixed list of six dummy Hyderabad-area restaurants - it exists purely to give this page something
real to call, not as a production restaurant API:

| Endpoint | Wraps |
|---|---|
| `GET /api/v1/geo/simulator/restaurants` | the dummy restaurant list |
| `GET /api/v1/geo/simulator/nearby?lat=&lng=&limit=` | `DistanceCalculator.topNNearest` + `etaMinutes` |
| `GET /api/v1/geo/simulator/distance-matrix?lat=&lng=` | `DistanceCalculator.distanceMatrix` |
| `GET /api/v1/geo/simulator/route?fromLat=&fromLng=&toLat=&toLng=` | `RoutingEngine.route` |

Run `pureeats-app` (needs a real database configured - see its own `application.yml`), tick "Live
backend" in this page's header, and the nearby/route panels call these for real; leave it off (the
default) and everything runs as the client-side JS simulation described above. See
`GeoSimulatorControllerTest` in `pureeats-app` for a fast, database-free unit test of this
controller's logic.

## What's simplified vs. the real backend

- **No persistence** - zones live in an in-memory JS array for the lifetime of the page, same spirit
  as `InMemoryPolygonBoundaryService` (no PostGIS, no database).
- **KD-Tree is a sort** - with a handful of demo zones/restaurants, sorting by distance and taking
  the top-N produces the identical result to a real KD-Tree k-nearest-neighbor query; the real
  backend uses an actual `KDTree<T>` because at real scale (thousands of zones) a linear sort stops
  being cheap.
- **One centroid per zone** - the real `GeoBoundary.geoHashPrefixes` can list several cells a large
  polygon spans; this demo buckets each zone by a single centroid hash for simplicity.
- **Road routing isn't the backend's own** - `RoutingEngine`'s real road-graph providers
  (GraphHopper/Dijkstra/A*) are still placeholders in Java (see `pureeats-geo-service/docs/README.md`),
  so this page's "all routing paths" view calls OSRM's public demo router directly for genuine road
  geometry instead. The live backend toggle still calls the real `RoutingEngine` bean - today that
  means the straight-line estimate, shown as a labeled reference line alongside OSRM's roads.

None of these simplifications change the *shape* of the pipeline - they're exactly what you'd
relax first when moving from "understand the algorithm" to "make it fast at scale," which is the
whole point of showing them clearly here rather than hiding the gap.
