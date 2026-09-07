# Geo Service — Distance, Routing & Delivery-Boundary Architecture

`pureeats-geo-service` is a shared library module (same shape as `domain` — plain classes plus a
`@Configuration`, no JPA entities of its own, not independently runnable) holding
location/distance-related code that used to live inside `pureeats-catalog-service`. Both
`pureeats-catalog-service` and `pureeats-order-service` depend on it directly.

A companion diagram, **[architecture-map.html](architecture-map.html)**, covers everything below
visually. Most of what used to be "proposed" here is now real code — solid boxes, with inline
`[REAL]` / `[NAIVE]` / `[THROWS]` / `[PLACEHOLDER]` tags marking exactly how complete each piece is.
Dashed gray boxes marked **PROPOSED** / **NOT CREATED** genuinely don't exist in code yet.

## What's real today

| Class | Package | What it does |
|---|---|---|
| `DistanceCalculator` (interface) | `com.pureeats.geo.distance` | `distanceKm(lat1, lng1, lat2, lng2): BigDecimal` — never throws, unparseable input yields `ZERO`. Plus a fuller default-method API — see below. |
| `HaversineDistanceCalculator` | `com.pureeats.geo.distance` | Default — great-circle distance |
| `EuclideanDistanceCalculator` | `com.pureeats.geo.distance` | Flat-plane approximation — demo-only alternative |
| `GoogleDistanceMatrixCalculator` | `com.pureeats.geo.distance` | Real road distance via Google's API; falls back to Haversine with no key or on failure |
| `DistanceCalculatorConfig` | `com.pureeats.geo.distance` | Picks the active implementation via `pureeats.distance.provider` (`@ConditionalOnProperty`) |
| `RoutingEngine` (interface) + `StraightLineRoutingEngine` | `com.pureeats.geo.routing` | `route(...): Route`. Only `StraightLineRoutingEngine` (wraps `DistanceCalculator`) has real logic; `GraphHopperRoutingEngine`/`DijkstraRoutingEngine`/`AStarRoutingEngine` are placeholders (see below) |
| `RoutingEngineConfig` | `com.pureeats.geo.routing` | Picks the active `RoutingEngine` via `pureeats.routing.provider` |
| `OwnerType`, `GeoBoundary` | `com.pureeats.geo.polygon` | Generic owner-identity value types for delivery-boundary polygons |
| `PolygonBoundaryService` (interface) + `InMemoryPolygonBoundaryService` | `com.pureeats.geo.polygon` | Real point-in-polygon logic, in-memory data (not yet persisted) |
| `PolygonBoundaryServiceConfig` | `com.pureeats.geo.polygon` | **The feature flag** — `pureeats.geo.polygon-boundary.enabled` |
| `GeoHash`, `KDTree<T>`, `PointInPolygon` | `com.pureeats.geo.index` / `com.pureeats.geo.polygon` | Real, tested, dependency-free algorithms (base32 geohash, 2D k-nearest-neighbor, even-odd ray casting) |

Consumed by `OrderPricingService`/`DeliveryRadiusRule` (order-service) and
`RestaurantService.checkDeliveryArea`/`findNearby` (catalog-service). See
`pureeats-order-service/docs/README.md` for how those call sites use it.

## Why `DistanceCalculator` keeps its name

The interface stays narrow and honestly named for exactly what it does — computing a distance —
rather than being renamed to something broad like `LocationService` now that the module is
reusable elsewhere. Real capabilities get their **own** narrowly-named interface living alongside
`DistanceCalculator` in this same module (`RoutingEngine`, `PolygonBoundaryService`), not a single
fat interface trying to do everything. Matches how every other interface in this codebase is named
(`CartValidationRule`, `DiscountCalculator` — one job each).

## `DistanceCalculator`'s fuller default-method API

Rather than a separate interface, the extra methods are `default` methods on `DistanceCalculator`
itself — none of the three implementations above override any of them, they all get every method
for free:

| Method | Status |
|---|---|
| `isWithinRadius(center, point, radiusKm)` | **[REAL]** `distanceKm(...) <= radiusKm` |
| `boundingBox(center, radiusKm)` | **[REAL]** same degree-per-km math `RestaurantService.findNearby` already inlines |
| `topNNearest(origin, candidates, n, locationOf)` | **[REAL]** generic over any caller-supplied candidate list — no domain-type coupling |
| `nearestAvailableRider(pickup, candidates, locationOf)` | **[REAL]** a 1-result case of `topNNearest` |
| `distanceMatrix(origins, destinations)` | **[REAL, naive O(n×m)]** loops `distanceKm` per pair; a provider-backed override could do one batch call |
| `etaMinutes(lat1, lng1, lat2, lng2)` | **[NAIVE]** flat 25 km/h assumption — override once a `RoutingEngine` exists |
| `nearby(lat, lng, radiusKm, limit)` | **[THROWS]** `UnsupportedOperationException` — needs a candidate data source this interface can't have |
| `geocode(address)` / `reverseGeocode(lat, lng)` | **[THROWS]** `UnsupportedOperationException` — needs an external geocoding provider |

## Routing engine

`RoutingEngine.route(lat1, lng1, lat2, lng2): Route` is a separate interface from
`DistanceCalculator` (a route needs a road graph to traverse, which nothing here has). Selected via
`pureeats.routing.provider` in `RoutingEngineConfig`, same `@ConditionalOnProperty` pattern as
`DistanceCalculatorConfig`:

- `straight-line` (default) — **[REAL]**, wraps the active `DistanceCalculator`, `polyline = null`.
- `graphhopper` / `dijkstra` / `astar` — **[PLACEHOLDER]**. Each is a real class that compiles and
  runs, but with no road-graph data to actually route over, every call logs a warning and falls back
  to the straight-line estimate. Swapping in real logic later changes only that one class's body.

## Store delivery boundaries via polygon mapping

Generic owner identity, not a hard-coded `restaurant_id`:

- **`OwnerType`** (enum) — `RESTAURANT` (today's only real user), `WAREHOUSE`, `HUB`, `VENDOR`. **[REAL]**
- **`GeoBoundary`** (record) — `id, ownerType, ownerId, vertices: List<LatLng>, geoHashPrefixes`. An
  in-memory value type today; a persisted version would live wherever the owner entity lives
  (`catalog-service`, for restaurants), the same way `DistanceCalculator` stays generic while its
  callers own the domain entities. **[REAL]**
- **`PolygonBoundaryService`** (interface) — `containsPoint(ownerType, ownerId, lat, lng): boolean`,
  `ownersContaining(ownerType, lat, lng): List<Long>`. **[REAL]**
- **`InMemoryPolygonBoundaryService`** — the "dummy" implementation: no persistence (`register(...)`
  just holds data in a `ConcurrentHashMap`), but the query algorithm is genuinely real, running the
  exact three-stage pipeline below. **[REAL algorithm, in-memory data]**
- **`GeoHash`**, **`KDTree<T>`**, **`PointInPolygon`** — real, tested, dependency-free (no JTS
  needed) implementations of base32 geohash encoding, a 2D k-nearest-neighbor index, and even-odd
  ray-casting point-in-polygon. **[REAL, tested]**

### The feature flag

`PolygonBoundaryServiceConfig` only registers a `PolygonBoundaryService` bean when
`pureeats.geo.polygon-boundary.enabled=true`. No property (or `false`) means **no bean exists at
all** — this is the entire "apply this rule or ignore it" switch.

### Where it plugs in

Not created yet, by design — see the diagram's dashed "NOT CREATED" box for the full reasoning.
Summary: a new `DeliveryPolygonRule implements CartValidationRule` in `pureeats-order-service`,
annotated `@ConditionalOnBean(PolygonBoundaryService.class)`, would run *alongside* (not instead of)
`DeliveryRadiusRule` — an address must pass the radius check **and** (once enabled) the polygon
check. Flipping `pureeats.geo.polygon-boundary.enabled=true` in *this* module is then the entire
activation — `CartValidationService`'s auto-collected rule list picks the new rule up automatically,
with zero other code changes; flipping it back off removes it just as automatically.

One real prerequisite stands in the way today: `CartValidationContext` only carries a precomputed
`distanceKm`, not the raw `customerLat`/`customerLng` a point-in-polygon test needs. Those two
fields would need adding (threaded through `buildContext` and `assertPlaceable`) before
`DeliveryPolygonRule` can be written for real — a small, additive, low-risk change, but a real one,
which is why it's documented here rather than made without being asked.

## End-to-end flows

**Flow A — user search** (`InMemoryPolygonBoundaryService.ownersContaining`, all three stages real):

```
1. User searches nearby stores        (entry point - no geo-service call yet)
2. Find nearby GeoHash buckets        -> GeoHash.encode(...) prefix match          [REAL]
3. KD-Tree finds nearest stores       -> KDTree.kNearest(...) narrows candidates   [REAL]
4. Validate polygon coverage          -> PointInPolygon.contains(...)             [REAL]
5. Return only deliverable stores     -> the stage-4 survivors
```

**Flow B — order placement, with the optional polygon gate**:

```
OrderService.placeOrder -> cartValidationService.assertPlaceable(...)
  1. DeliveryRadiusRule    - always runs, always registered
  2. DeliveryPolygonRule   - proposed; only registered if pureeats.geo.polygon-boundary.enabled=true
  3. assertPlaceable(...) throws on the first issue found among ALL registered rules
```

Today only step 1 exists, so this is exactly current behavior. Turning the flag on adds step 2 with
no other code change — once `DeliveryPolygonRule` itself is written (see "where it plugs in" above).

## Explicitly out of scope for this module

Worth naming these so scope creep doesn't happen by accident: **multi-store ranking/matching**,
**dynamic surge pricing**, **live driver GPS tracking** (e.g. Redis GEO), **multi-drop route
optimization/batching**, **ML-based ETA prediction**, and a **live WebSocket ops dashboard**. All of
these are real, valuable ideas — but they're dispatch/order-domain decisions that would *consume*
this module's primitives (a distance number, a route, a yes/no polygon answer), not things a
distance/location library should compute itself. If/when built, they belong in `order-service` (or
a future dedicated dispatch module), calling into `pureeats-geo-service` the same way
`OrderPricingService` already does.
