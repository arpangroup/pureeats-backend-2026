## Store Delivery Boundary System Using Polygon Mapping + GeoHash + KD-Tree 🚚🗺️

In a real-world delivery platform, a store owner should be able to define one or more custom delivery regions directly on a map.

The store owner can draw polygon boundaries representing areas where delivery is supported. These polygon regions are then stored in the database and integrated with the existing geo-spatial 

When a user searches for nearby stores or places an order, the system must:
1. Detect the user’s location
2. Verify whether the user lies inside any delivery polygon
3. Filter stores based on delivery coverage
4. Use GeoHash and KD-Tree optimizations for fast querying


The goal is to integrate polygon-based delivery boundaries into the existing geo-spatial system efficiently and visually.

## 🧠 Real-World Example

Imagine:

```
Restaurant A
    ├── Hyderabad Downtown Zone
    ├── HiTech City Zone
    ├── Jubilee Hills Zone
```
Each zone is a polygon drawn manually on the map.

Users inside polygon:

✅ Delivery available

Users outside polygon:

❌ Delivery unavailable

## Final System Architecture
```
Store Owner
     ↓
Draw Polygon On Map
     ↓
Frontend Sends Coordinates
     ↓
Spring Boot Backend
     ↓
Store Polygon In DB (PostGIS)
     ↓
Generate GeoHash Buckets
     ↓
Build KD-Tree Spatial Index
     ↓
User Searches Nearby
     ↓
GeoHash Prefilter
     ↓
KD-Tree Optimization
     ↓
Point-In-Polygon Validation
     ↓
Return Supported Stores
```

---

🧱 STEP 1 — What You Are Building

You will build:
- ✅ Polygon drawing system
- ✅ Delivery zone management
- ✅ GeoHash optimization
- ✅ KD-Tree optimization
- ✅ Point-in-polygon detection
- ✅ Spatial search engine

---

## DATA MODEL (POSTGIS)
We store polygons in PostGIS.

**Store table**
```sql
CREATE TABLE store (
    id BIGSERIAL PRIMARY KEY,
    name TEXT,

    delivery_area GEOGRAPHY(POLYGON, 4326)
);
```

**INDEXING (VERY IMPORTANT)**
```sql
CREATE INDEX idx_store_delivery_area
ON store
USING GIST(delivery_area);
```


🌍 STEP 2 — Draw Polygon on Map
Use Leaflet Draw plugin.

```html
<link rel="stylesheet"
 href="https://unpkg.com/leaflet/dist/leaflet.css"/>

<link rel="stylesheet"
 href="https://cdnjs.cloudflare.com/ajax/libs/leaflet.draw/1.0.4/leaflet.draw.css"/>

<script src="https://unpkg.com/leaflet/dist/leaflet.js"></script>

<script src="https://cdnjs.cloudflare.com/ajax/libs/leaflet.draw/1.0.4/leaflet.draw.js"></script>
```

## 🗺️ Initialize Map
```javascript
const map = L.map('map')
    .setView([17.385, 78.486], 13);

L.tileLayer(
    'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',
    maxZoom: 19
).addTo(map);
```


## ✏️ Enable Polygon Drawing
```javascript
// Feature group to store drawn shapes
const drawnItems = new L.FeatureGroup();
map.addLayer(drawnItems);

// Draw controls
const drawControl = new L.Control.Draw({
    edit: { featureGroup: drawnItems },
    draw: {
        polygon: true,
        rectangle: false,
        circle: false,
        marker: false,
        polyline: false
    }
});
map.addControl(drawControl);


// ON DRAW EVENT
map.on(L.Draw.Event.CREATED, function (event) {
  const layer = event.layer;
  drawnItems.addLayer(layer);
  const geojson = layer.toGeoJSON();
  console.log("Polygon GeoJSON:", geojson);

  // send to backend
  fetch("/store/delivery-area", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(geojson)
  });
});
```

WHAT FRONTEND SENDS

Example GeoJSON:
```json
{
  "type": "Feature",
  "geometry": {
    "type": "Polygon",
    "coordinates": [[
      [78.48, 17.38],
      [78.50, 17.38],
      [78.50, 17.40],
      [78.48, 17.40],
      [78.48, 17.38]
    ]]
  }
}
```


🧠 What Happens?

Store owner can now:
- ✅ Draw polygon
- ✅ Edit polygon
- ✅ Resize polygon
- ✅ Delete polygon

Exactly like delivery platforms.

---

## 📍 STEP 3 — Capture Polygon Coordinates
When polygon is drawn:

```JavaScript
map.on(L.Draw.Event.CREATED, function (event) {
    const layer = event.layer;
    drawnItems.addLayer(layer);
    const coordinates = layer.getLatLngs()[0];
    console.log(coordinates);
    sendPolygon(coordinates);
});
```

### 🌐 Example Polygon Coordinates
```json
[
  { "lat": 17.385, "lng": 78.486 },
  { "lat": 17.390, "lng": 78.492 },
  { "lat": 17.380, "lng": 78.500 }
]
```

## 🚀 STEP 4 — Send Polygon to Backend
```JavaScript
function sendPolygon(coords) {
    fetch("/delivery-zone", {
        method: "POST",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify(coords)
    });
}
```

---

## 🧱 STEP 5 — Backend Polygon API

DeliveryZoneController.java
```java
@RestController
@RequestMapping("/delivery-zone")
public class DeliveryZoneController {

    @PostMapping
    public String saveZone(@RequestBody List<PointDTO> points) {
        return "Polygon saved";
    }
}
````

or

```java
@RestController
@RequestMapping("/store")
public class StoreController {

    @Autowired
    private StoreService storeService;

    @PostMapping("/delivery-area")
    public String saveArea(@RequestBody Map<String, Object> geoJson) {
        storeService.savePolygon(geoJson);
        return "Saved";
    }
}
```

---

## 🧠 STEP 6 — Store Polygon in Database
We convert GeoJSON → WKT (Well Known Text) <br/>
Use PostGIS geometry type.

DeliveryZoneEntity.java
```java
@Entity
@Table(name = "delivery_zone")
public class DeliveryZoneEntity {

    @Id
    @GeneratedValue
    private Long id;

    private Long storeId;

    @Column(columnDefinition = "geometry(Polygon,4326)")
    private Polygon polygon;
}
```

🌍 Why PostGIS?

PostGIS provides:
- ✅ Polygon storage
- ✅ Spatial indexing
- ✅ Point-in-polygon search
- ✅ Fast geo queries
- ✅ Production-grade GIS support

---

## 🧠 STEP 7 — Convert Coordinates to Polygon
Use JTS Geometry library.

### Maven Dependency
```xml
<dependency>
    <groupId>org.locationtech.jts</groupId>
    <artifactId>jts-core</artifactId>
</dependency>
```


## Polygon Builder
```java
public Polygon buildPolygon(List<PointDTO> points) {
    GeometryFactory factory = new GeometryFactory();

    Coordinate[] coordinates = new Coordinate[points.size() + 1];

    for (int i = 0; i < points.size(); i++) {
        PointDTO p = points.get(i);
        coordinates[i] = new Coordinate(p.getLng(), p.getLat());
    }

    coordinates[points.size()] = coordinates[0];

    LinearRing ring = factory.createLinearRing(coordinates);
    return factory.createPolygon(ring);
}
```
🧠 Important Concept
- Polygon must be CLOSED.
- Meaning:
    - First point == Last point

---

## Option2: PARSE POLYGON + SAVE (POSTGIS)
We convert GeoJSON → WKT (Well Known Text)

Example WKT format:
```
POLYGON((78.48 17.38, 78.50 17.38, 78.50 17.40, 78.48 17.40, 78.48 17.38))
```

Service Layer
```java
@Service
public class StoreService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public void savePolygon(Map<String, Object> geoJson) {

        List<List<Double>> coords = extractCoordinates(geoJson);
        String wkt = buildWKT(coords);

        String sql = """
            INSERT INTO store(name, delivery_area)
            VALUES (?, ST_GeomFromText(?, 4326))
        """;

        jdbcTemplate.update(sql,
                "Demo Store",
                wkt
        );
    }
}
```

**Build WKT**
```java
private String buildWKT(List<List<Double>> coords) {

    StringBuilder sb = new StringBuilder("POLYGON((");

    for (List<Double> c : coords) {
        sb.append(c.get(0))
          .append(" ")
          .append(c.get(1))
          .append(",");
    }
    sb.setLength(sb.length() - 1); // remove last comma
    sb.append("))");
    return sb.toString();
}
```

## CUSTOMER DELIVERY CHECK (CORE LOGIC)

SQL QUERY (VERY IMPORTANT)
```sql
SELECT *
FROM store
WHERE ST_Contains(
    delivery_area,
    ST_Point(78.49, 17.39)
);
```

Spring Service
```java
public boolean isDeliverable(double lat, double lon) {

    String sql = """
        SELECT COUNT(*)
        FROM store
        WHERE ST_Contains(
            delivery_area,
            ST_Point(?, ?)
        )
    """;

    Integer count = jdbcTemplate.queryForObject(
            sql,
            Integer.class,
            lon, lat
    );

    return count != null && count > 0;
}
```



---


## 📦 STEP 8 — GeoHash Optimization
Polygon searching over all stores is expensive.

So we optimize.

**Strategy**
```
Polygon
   ↓
Generate GeoHash buckets
   ↓
Store bucket references
   ↓
Search nearby buckets first
```

Example
```
Polygon Area
   ↓
tepg1
tepg2
tepg3
```

---

## 📍 STEP 9 — KD-Tree Integration
KD-Tree improves nearest-neighbor searching.

KD-Tree Stores
```
Store Center Points
Driver Points
Restaurant Points
```

Search Flow
```
User Location
    ↓
GeoHash Prefilter
    ↓
KD-Tree Nearby Search
    ↓
Point-In-Polygon Validation
``` 

---

## 📍 STEP 10 — Point-In-Polygon Validation
Now verify user is inside delivery region.

Java Implementation

```java
public boolean insidePolygon(Polygon polygon, double lat, double lon) {

    GeometryFactory factory = new GeometryFactory();

    Point point = factory.createPoint(new Coordinate(lon, lat));
    return polygon.contains(point);
}
```

---

## STEP 11 — User Search Flow
Now implement full search logic.

Search Algorithm
```
1. User searches nearby stores
2. Find nearby GeoHash buckets
3. KD-Tree finds nearest stores
4. Validate polygon coverage
5. Return only deliverable stores
```

🌐 Example
```
User Location:
17.387, 78.489

Nearby Stores:
Store A ✅
Store B ❌
Store C ✅
```

---

## 🚀 STEP 12 — Visualize Polygon on Map
Load saved polygon from backend.

Frontend Rendering
```JavaScript
L.polygon([
    [17.385, 78.486],
    [17.390, 78.492],
    [17.380, 78.500]

], {
    color: "blue"
}).addTo(map);
```

🌐 What You Will See
```
🟦 Delivery Boundary
🔴 Drivers
⚫ Users
🟢 Supported Regions
```

---

# 🚀 Advanced Improvements

1. Dynamic Delivery Zones <br/>
   Automatically expand or shrink polygon based on:
    - Traffic
    - Driver availability
    - Weather
    - Peak demand

2. Heatmaps
   Visualize: `High demand areas`
3. H3 Hexagonal Indexing <br/>
   Upgrade from GeoHash to: `Uber H3 indexing`
4. Real-Time Driver Streaming <br/>
    - Kafka
    - Redis Streams
    - WebSockets
5. Polygon Simplification <br/>
   Optimize large polygons using: `Douglas-Peucker algorithm`


# 🚀 NEXT STEP
1. Multi-store matching engine (Uber Eats full flow)
2. Live driver tracking + delivery assignment
3. Dynamic surge pricing per polygon
4. Route optimization (multi-drop delivery)



---


# 1. MULTI-STORE MATCHING ENGINE
Customer may match multiple stores:
- Store A (fast delivery)
- Store B (cheaper)
- Store C (high rating)
  We rank them.

## STEP 1 — STORE SCORE MODEL
We compute:
```
Score = distance + price factor + rating + delivery time + surge penalty
```
Formula:

$$
Score=w1​D+w2​P+w3​R+w4​T+w5​S​
$$

Store Entity
```java
class Store {
    Long id;
    String name;
    double lat;
    double lon;
    double rating;
    double baseDeliveryTime;
}
```

## STEP 2 — MATCH STORES
```java
@Service
public class StoreMatchingService {

    @Autowired
    private StoreRepository storeRepo;

    @Autowired
    private SurgeService surgeService;

    public List<Store> findBestStores(double lat, double lon) {
        List<Store> stores = storeRepo.findNearbyCandidates(lat, lon);
        List<ScoredStore> scored = new ArrayList<>();

        for (Store s : stores) {
            double distance = haversine(lat, lon, s.getLat(), s.getLon());
            double surge = surgeService.getSurgeForStore(s.getId());

            double score =
                    distance * 0.5 +
                    (1 / s.getRating()) * 0.2 +
                    s.getBaseDeliveryTime() * 0.2 +
                    surge * 0.1;

            scored.add(new ScoredStore(s, score));
        }

        return scored.stream()
                .sorted(Comparator.comparingDouble(ScoredStore::score))
                .map(ScoredStore::store)
                .toList();
    }
}
```

## 2. DYNAMIC SURGE PRICING (PER POLYGON)
This is Uber Eats pricing brain.

### 💡 IDEA

Each delivery zone (polygon) has:
- demand
- supply
- surge multiplier

## STEP 1 — ZONE MODEL
```java
class DeliveryZone {
    Long id;
    String geoHash;
    int activeOrders;
    int availableDrivers;
}
```

## STEP 2 — SURGE FORMULA

$$
\text{Surge} = \frac{\text{Demand}}{\text{Supply} + 1}
$$

Service:
```java
@Service
public class SurgeService {

    private final Map<String, DeliveryZone> zones = new ConcurrentHashMap<>();

    public double getSurgeForStore(Long storeId) {

        DeliveryZone zone = zones.get(getZoneKey(storeId));

        if (zone == null) return 1.0;

        double surge = (double) zone.getActiveOrders()
                / (zone.getAvailableDrivers() + 1);

        return Math.max(1.0, Math.min(surge, 3.0));
    }

    public void incrementDemand(String zone) {
        zones.computeIfAbsent(zone, z -> new DeliveryZone())
             .activeOrders++;
    }

    public void incrementSupply(String zone) {
        zones.computeIfAbsent(zone, z -> new DeliveryZone())
             .availableDrivers++;
    }
}
```

# 3. MULTI-DROP ROUTE OPTIMIZATION
This is delivery batching system (**Swiggy Genie / Uber Delivery**).

PROBLEM

Driver gets:
- Pickup A → Drop A
- Pickup B → Drop B
- Pickup C → Drop C
  Need optimal route.

### MODEL = GRAPH PROBLEM
- Each location = node
- Each travel cost = edge

### DELIVERY TASK MODEL
```java
class DeliveryTask {
    Location pickup;
    Location drop;
}
```

## STEP 1 — BUILD ROUTE GRAPH
We generate distance matrix:
```
A → B → C → D
```

## STEP 2 — ROUTE OPTIMIZATION (GREEDY + A*)
We use hybrid:
- ✔ nearest pickup first
- ✔ shortest path reorder

SIMPLE VERSION (REAL SYSTEM START POINT)
```java
public List<Location> optimizeRoute(List<Location> stops, Location start) {
    List<Location> route = new ArrayList<>();
    Location current = start;

    while (!stops.isEmpty()) {

        Location nearest = null;
        double best = Double.MAX_VALUE;

        for (Location l : stops) {
            double d = haversine(
                    current.getLat(), current.getLon(),
                    l.getLat(), l.getLon()
            );

            if (d < best) {
                best = d;
                nearest = l;
            }
        }

        route.add(nearest);
        stops.remove(nearest);
        current = nearest;
    }
    return route;
}
```

### ⚡ ADVANCED VERSION (REAL UBER)
Use:
- A*
- Time windows
- Traffic graph
- ML-based ETA

---

# LIVE DRIVER TRACKING + DELIVERY ASSIGNMENT

## REDIS GEO STORAGE
```java
GEOADD drivers 78.48 17.38 driver_1
```

## LIVE UPDATE SERVICE
```java
@Service
public class DriverTrackingService {
    @Autowired
    private StringRedisTemplate redis;

    private static final String KEY = "drivers_geo";

    public void updateLocation(Long driverId, double lat, double lon) {
        redis.opsForGeo()
                .add(KEY,
                        new Point(lon, lat),
                        driverId.toString());
    }
}
```

## FIND NEAREST DRIVER (ASSIGNMENT)
```java
public String assignDriver(double lat, double lon) {

    Circle circle = new Circle(
            new Point(lon, lat),
            new Distance(5, Metrics.KILOMETERS)
    );

    GeoResults<RedisGeoCommands.GeoLocation<String>> results =
            redis.opsForGeo().radius(KEY, circle);

    return results.getContent().get(0)
            .getContent()
            .getName();
}
```

## DELIVERY ASSIGNMENT FLOW
```
Order created
   ↓
Find store
   ↓
Find driver (Redis GEO)
   ↓
Assign driver
   ↓
Start tracking
   ↓
Update route dynamically
```

## FINAL FULL SYSTEM FLOW
```
Customer Order
   ↓
Multi-store matching engine
   ↓
Polygon check (PostGIS)
   ↓
Surge pricing engine
   ↓
Driver assignment (Redis GEO)
   ↓
Route optimization (multi-drop)
   ↓
Live tracking updates
   ↓
Delivery completion
```

---


# ML-BASED ETA PREDICTION SYSTEM
WHY RULE-BASED ETA FAILS?

Earlier we used:
```
ETA = distance / speed
```

❌ Problems:
- ignores traffic
- ignores time of day
- ignores road type
- ignores weather
- ignores driver behavior

## REAL UBER APPROACH
Uber uses ML model:
```
ETA = f(distance, traffic, road, driver, time, region)
```

## FEATURE ENGINEERING (MOST IMPORTANT)
We convert ride into features:

## 🔢 INPUT FEATURES
**Spatial:**
- distance_km
- route_curvature
- number_of_turns
  **Temporal:**
- hour_of_day
- day_of_week
- peak_hour_flag
  **Traffic:**
- congestion_level
- avg_speed_zone
  **Driver:**
- driver_speed_avg
- driver_history_delay
  **Context:**
- rain / weather
- event zone

## FEATURE VECTOR
```
X = [
 distance,
 hour,
 traffic,
 driver_speed,
 road_complexity
]
```

## ⚙️ MODEL OPTIONS
In production:
- Gradient Boosting (XGBoost) ⭐
- Random Forest
- Neural Networks (large scale)
- Deep Spatio-Temporal models (advanced Uber AI)


## 🧪 SIMPLE WORKING MODEL (JAVA SIMULATION)
We simulate ML inference (real backend style).

🎯 ETA MODEL SERVICE
```java
@Service
public class EtaPredictionService {

    public double predictETA(
            double distanceKm,
            int hourOfDay,
            double trafficLevel,
            double driverSpeed,
            double roadComplexity
    ) {

        // Simulated ML weights (like regression model)
        double eta =
                (distanceKm * 2.5) +
                (trafficLevel * 8) +
                (roadComplexity * 3) -
                (driverSpeed * 1.2) +
                (isPeakHour(hourOfDay) ? 5 : 0);

        return Math.max(1, eta); // minutes
    }

    private boolean isPeakHour(int hour) {
        return (hour >= 8 && hour <= 11) ||
               (hour >= 17 && hour <= 21);
    }
}
```

## 🧠 REAL ML VERSION (HOW IT LOOKS)
In real Uber system:
```
Spring Boot → Kafka → Feature Store → ML Model → ETA
```
Model is usually:
- Python (XGBoost / PyTorch)
- Served via REST/gRPC

## REAL CALL FLOW
```
Ride Request
   ↓
Feature Builder Service
   ↓
ML Model Server (Python)
   ↓
ETA Prediction
   ↓
Driver Matching + Pricing
```

---

# PART 2 — REAL-TIME MAP DASHBOARD (UBER CONTROL PANEL)

WHAT YOU WILL BUILD

A live dashboard showing:
- 🟢 Drivers (moving in real time)
- 🏪 Stores (delivery zones)
- 🚗 Active rides
- 🔥 Surge heatmap
- 📍 Assignments live


## SYSTEM ARCHITECTURE
```
Driver GPS Updates
      ↓
Redis GEO
      ↓
WebSocket Publisher
      ↓
Frontend Map Dashboard
```

## 📡 STEP 1 — WEBSOCKET BACKEND

WebSocket Config
```java
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*").withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }
}
```

## 🚗 STEP 2 — DRIVER STREAM SERVICE
```java
@Service
public class DriverStreamService {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    public void publishDriverLocation(Long driverId, double lat, double lon) {

        Map<String, Object> payload = new HashMap<>();
        payload.put("driverId", driverId);
        payload.put("lat", lat);
        payload.put("lon", lon);

        messagingTemplate.convertAndSend("/topic/drivers", payload);
    }
}
```

## 📍STEP 3 — DRIVER UPDATE FLOW
```java
public void updateDriverLocation(Long id, double lat, double lon) {

    // 1. Redis update
    redisGeo.update(id, lat, lon);

    // 2. Push to dashboard
    streamService.publishDriverLocation(id, lat, lon);
}
```

## 🖥️ STEP 4 — FRONTEND DASHBOARD (LIVE MAP)

HTML + Leaflet + WebSocket
```html
<!DOCTYPE html>
<html>
<head>
  <title>Uber Control Panel</title>

  <link rel="stylesheet"
        href="https://unpkg.com/leaflet/dist/leaflet.css"/>

  <script src="https://unpkg.com/leaflet/dist/leaflet.js"></script>
  <script src="https://cdn.jsdelivr.net/npm/sockjs-client/dist/sockjs.min.js"></script>
  <script src="https://cdn.jsdelivr.net/npm/stompjs/lib/stomp.min.js"></script>

  <style>
    #map { height: 100vh; }
  </style>
</head>

<body>

<div id="map"></div>

<script>

const map = L.map('map').setView([17.385, 78.486], 13);

L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
  maxZoom: 19
}).addTo(map);

const markers = {};

// WebSocket connect
const socket = new SockJS('/ws');
const stomp = Stomp.over(socket);

stomp.connect({}, function () {

    stomp.subscribe('/topic/drivers', function (msg) {

        const data = JSON.parse(msg.body);

        const id = data.driverId;

        if (markers[id]) {
            map.removeLayer(markers[id]);
        }

        markers[id] = L.circleMarker([data.lat, data.lon], {
            radius: 6,
            color: "blue"
        }).addTo(map)
        .bindPopup("Driver " + id);
    });
});

</script>

</body>
</html>
```

## STEP 5 — ADD SURGE HEATMAP (OPTIONAL ADVANCED)
You can overlay:
- red zones = high demand
- green zones = low demand

Example:
```JavaScript
L.circle([17.39, 78.48], {
  radius: 500,
  color: "red",
  fillOpacity: 0.2
}).addTo(map);
```