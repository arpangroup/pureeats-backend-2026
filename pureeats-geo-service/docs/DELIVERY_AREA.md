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
  const geojson = layer.toGeoJSON(); // Convert Polygon to GeoJSON
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

Polygon Request DTO
```java
public record DeliveryAreaRequest(
        String type, 
        GeometryDTO geometry 
) { }

public record GeometryDTO(
        String type, 
        List<List<List<Double>>> coordinates 
) { }
```

DeliveryZoneController.java
```java
@RestController
@RequestMapping("/api/stores/delivery-zone")
public class DeliveryZoneController {

    @PostMapping("/{storeId}/delivery-area")
    public String saveZone(
            @PathVariable Long storeId,
            @RequestBody DeliveryAreaRequest request) {
      service.saveDeliveryArea(storeId, request ); 
      return ResponseEntity.ok().build();
    }
}
````

## 🧠 STEP 6 — Validate Polygon

### Step 6.1: Validate Polygon
- Polygon type 
- Coordinate count 
- Closed ring 
- Valid geometry 
- Self-intersection 
- Maximum area 
- Maximum number of vertices

### Step 6.2: Polygon Must Be Closed
```text
A → B → C → D → A
```

Example:
```text
First point: [78.48, 17.38] 
Last point : [78.48, 17.38]
```

### Step 6.3: Minimum Number of Points
A polygon requires at least three distinct points.

Three points form the smallest polygon. The closing point is then added:
```text
A → B → C → A
```

### Step 6.4: Invalid Polygon Detection
JTS can validate the geometry.
```java
if (!polygon.isValid()) { 
    throw new IllegalArgumentException( "Invalid delivery polygon" ); 
}
```

### Step 6.5: Polygon Self-Intersection

This is invalid:

```text
      A──────B
       \    /
        \  /
        /  \
       /    \
      C──────D
```

A self-intersecting polygon should be rejected.

This is another reason validation should happen before persistence.

---

## 🧠 STEP 7 — Store Polygon in Database

### Step 7.1: Create PostgreSQL Database
Enable PostGIS:

```sql
CREATE EXTENSION IF NOT EXISTS postgis;
```

Verify:

```sql
SELECT PostGIS_Version();
```


### Step 7.2: Enable PostGIS
PostGIS adds spatial functionality to PostgreSQL.

We convert GeoJSON → WKT (Well Known Text) <br/>

**Use PostGIS geometry type.**


Without PostGIS:

```text
PostgreSQL
    ↓
Normal relational data
```

With PostGIS:

```text
PostgreSQL
    ↓
Spatial database
    ↓
Point
Polygon
Geometry
Spatial indexes
Geo queries
```

### Step 7.3: Delivery Zone Table

```sql
CREATE TABLE delivery_zone (
   id BIGSERIAL PRIMARY KEY,
   store_id BIGINT NOT NULL,
   polygon GEOMETRY(POLYGON, 4326) NOT NULL,
   created_at TIMESTAMP NOT NULL,
   updated_at TIMESTAMP NOT NULL
);
```

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

### Step 7.4: Geometry Column

The important column is:

```sql
polygon GEOMETRY(POLYGON, 4326)
```

This tells PostGIS:

```text
Geometry type = Polygon
SRID = 4326
```

### Step 7.5: SRID 4326

EPSG:4326 represents:

```text
WGS 84
Latitude / Longitude
```

This is commonly used by:

```text
GPS
GeoJSON
Leaflet
OpenStreetMap
```

The key is to keep coordinate systems consistent.

---

🌍 Why PostGIS?

PostGIS provides:
- ✅ Polygon storage
- ✅ Spatial indexing
- ✅ Point-in-polygon search
- ✅ Fast geo queries
- ✅ Production-grade GIS support

---

## 🧠 STEP 8 — Convert Coordinates to Polygon (Store Polygon Using JTS)
Use JTS Geometry library.

### Maven Dependency
```xml
<dependency>
    <groupId>org.locationtech.jts</groupId>
    <artifactId>jts-core</artifactId>
</dependency>
```

```java
GeometryFactory factory = new GeometryFactory();
```

Then create:

```text
Coordinate[]
LinearRing
Polygon
```


## Option1: Polygon Builder - GeometryFactory
```java
public Polygon buildPolygon(List<PointDTO> points) {
    GeometryFactory factory = new GeometryFactory();

    // Create coordinates:
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

## Option2: Store Polygon Using WKT - PARSE POLYGON + SAVE (POSTGIS)
We convert GeoJSON → WKT (Well Known Text)

Example WKT format:
```
POLYGON((
  78.48 17.38, 
  78.50 17.38, 
  78.50 17.40, 
  78.48 17.40, 
  78.48 17.38
 ))
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

## GeoJSON → WKT
The transformation:

```text
GeoJSON
   ↓
Extract Coordinates
   ↓
WKT
   ↓
PostGIS
```

## Build WKT (GeoJSON → WKT)
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


## 📦 STEP 9 - Save Polygon with PostGIS

Example:

```sql
INSERT INTO delivery_zone (
    store_id,
    polygon,
    created_at,
    updated_at
)
VALUES (
    ?,
    ST_GeomFromText(?, 4326),
    NOW(),
    NOW()
);
```

The important function is:

```sql
ST_GeomFromText(...)
```

---



## 📦 STEP 10 — GeoHash Optimization
Polygon searching over all stores is expensive. GeoHash converts a geographical location into a string representing a spatial cell.

Nearby coordinates tend to have similar GeoHash prefixes.


Example:

```text
17.385, 78.486
        ↓
    tepg1...
```

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
Example:
```text
Store A → tepg1
Store B → tepg1
Store C → tepg2
Store D → tepg3
```

Database:

```text
store_id | geohash
---------|--------
A        | tepg1
B        | tepg1
C        | tepg2
D        | tepg3
```

---

## Store GeoHash References


Possible table:

```sql
CREATE TABLE delivery_zone_geohash (
    delivery_zone_id BIGINT NOT NULL,
    geohash VARCHAR(20) NOT NULL
);
```

Example:

```text
zone 101 → tepg1
zone 101 → tepg2
zone 101 → tepg3
```

---

## 📍 STEP 11 — GeoHash Prefilter

User searches:

```text
17.387, 78.489
```

Generate:

```text
User GeoHash = tepg1
```

Search nearby cells:

```text
tepg1
tepg2
tepf9
tepg0
...
```

Then:

```text
Only stores associated with those cells
```

are considered.

---


## 📍 STEP 12 — KD-Tree Integration
GeoHash is useful for grid-based filtering.

KD-Tree is useful for:
> Nearest-neighbor search.

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

## What KD-Tree Solves

Without KD-Tree:

```text
Calculate distance to every store
```

With KD-Tree:

```text
User
 ↓
KD-Tree
 ↓
Nearest K stores
```

Example:

```text
User
 ↓
Nearest 20 stores
```

Then only those candidates need more expensive checks.

---

## Store Location Index

Create a point for each store:

```text
Store A → (78.486, 17.385)
Store B → (78.492, 17.390)
Store C → (78.500, 17.380)
```

Build:

```text
KD-Tree
```

from those points.

---


## 📍 STEP 13 — Point-In-Polygon Validation
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

## STEP 13 — User Search Flow
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

## 🚀 STEP 14 — Visualize Polygon on Map
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

# LIVE DRIVER TRACKING + DELIVERY ASSIGNMENT

## REDIS GEO STORAGE
```
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

## Step 15 - Retrieve Polygon

When the store owner opens the delivery area editor again:

```text
Frontend
   ↓
GET /delivery-area
   ↓
Backend
   ↓
PostGIS
   ↓
GeoJSON
   ↓
Leaflet
```

---

## PostGIS → GeoJSON

PostGIS provides:

```sql
ST_AsGeoJSON(polygon)
```

Example:

```sql
SELECT
    id,
    store_id,
    ST_AsGeoJSON(polygon)
FROM delivery_zone
WHERE store_id = ?;
```

This is extremely convenient because the frontend already understands GeoJSON.

---

## Render Saved Polygon

Frontend:

```javascript
fetch("/api/stores/100/delivery-area")
    .then(response => response.json())
    .then(data => {

        L.geoJSON(data)
            .addTo(map);
    });
```

Or manually:

```javascript
L.polygon([
    [17.385, 78.486],
    [17.390, 78.492],
    [17.380, 78.500]
]).addTo(map);
```

---

## ST_Contains

PostGIS provides:

```sql
ST_Contains()
```

Example:

```sql
SELECT *
FROM delivery_zone
WHERE ST_Contains(
    polygon,
    ST_SetSRID(
        ST_Point(78.489, 17.387),
        4326
    )
);
```

Notice:

```text
ST_Point(longitude, latitude)
```

---

##  ST_Within

The reverse relationship can be expressed using:

```sql
ST_Within(point, polygon)
```

Example:

```sql
SELECT *
FROM delivery_zone
WHERE ST_Within(
    ST_SetSRID(
        ST_Point(78.489, 17.387),
        4326
    ),
    polygon
);
```

---

## ST_Covers

There is an important boundary difference.

`ST_Contains` can exclude points lying exactly on the polygon boundary.

For delivery eligibility, you may prefer:

```sql
ST_Covers(polygon, point)
```

if your business rule says:

> Boundary locations are deliverable.

Therefore choose the spatial predicate according to the actual business requirement.

---
