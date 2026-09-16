# Public Transport Journey Planning & Real-Time Tracking Platform

A scalable, configurable and extensible system for searching, planning, ranking and tracking journeys across public transportation networks such as **Bus, Train and Metro**.

The system is designed using **SOLID principles, Strategy Pattern, Specification Pattern, Factory Pattern, modular architecture and configuration-driven business rules**.

The initial implementation can focus on buses, but the architecture should allow trains and metro to be introduced later without redesigning the core system.

---

## The important design idea is to separate:

1. **Transport network modeling** — stops, routes, trips, buses, schedules.
2. **Journey matching** — source → destination, direct and multi-leg journeys.
3. **Ranking** — fastest, fewest transfers, cheapest, least walking, etc.
4. **Configuration** — ranking weights, search rules, transfer rules, time windows.
5. **Extensibility** — future support for metro, train, cab, multimodal journeys, real-time GPS, dynamic pricing, etc.


### A bus system typically looks like:
```text
Stop → Route → Trip → Stop Time
```

### A train system has similar concepts:
```text
Station → Train Route → Train Trip → Station Time
```

---


# 1. Problem Statement

Design a public transportation platform that allows a user to:

- Search from a source to a destination.
- Select a departure or arrival time.
- Search for buses, trains, metro or combinations.
- Find direct journeys.
- Find journeys requiring transfers.
- Find multiple possible routes.
- Calculate journey duration.
- Calculate waiting time.
- Calculate walking distance.
- Calculate fare.
- Calculate number of transfers.
- Rank journeys using configurable criteria.
- Track a bus/train/metro in real time.
- Display current location.
- Display next stop/station.
- Display estimated arrival time.
- Display delays.
- Handle service cancellations and disruptions.
- Support user preferences.
- Support future AI/ML-based ranking.

---

# 2. Refined System Design Question

> **Design a scalable and configurable public transport journey planning and real-time tracking platform that allows users to search for journeys between two locations, find direct and multi-leg routes across multiple transport modes, rank the available journeys using configurable criteria, and track active vehicles with real-time location and ETA information.**

The system should follow:

```text
SOLID
Clean Architecture
Domain Driven Design principles
Strategy Pattern
Factory Pattern
Specification Pattern
Event-driven architecture where appropriate
Configuration-driven business rules
```

---

# 3. Goals

The system should be:

### Scalable

Support:

```text
Millions of users
Millions of stops/stations
Thousands of routes
Millions of scheduled trips
Large real-time vehicle streams
High search traffic
```

### Configurable

Business rules should not be hardcoded.

Example:

```text
Maximum Transfers = 2

Maximum Walking Distance = 1000m

Minimum Transfer Time = 5 minutes

Maximum Results = 20
```

### Extensible

The system should allow new:

```text
Transport modes
Routing algorithms
Ranking criteria
Fare calculation strategies
ETA providers
Real-time data providers
Search filters
User preferences
```

without modifying existing core logic.

---

# 4. High-Level Architecture

```text
                         ┌───────────────────────┐
                         │      Web / Mobile     │
                         └───────────┬───────────┘
                                     │
                                     ▼
                           ┌──────────────────┐
                           │   API Gateway    │
                           └────────┬─────────┘
                                    │
                    ┌───────────────┴────────────────┐
                    │                                │
                    ▼                                ▼
          ┌───────────────────┐            ┌──────────────────┐
          │ Journey Search    │            │ Tracking API     │
          │ Service           │            │                  │
          └─────────┬─────────┘            └────────┬─────────┘
                    │                               │
                    ▼                               ▼
          ┌───────────────────┐            ┌──────────────────┐
          │ Routing Engine    │            │ Real-Time Engine │
          └─────────┬─────────┘            └────────┬─────────┘
                    │                               │
          ┌─────────┼──────────┐          ┌─────────┼──────────┐
          ▼         ▼          ▼          ▼         ▼          ▼
        BUS       TRAIN      METRO       GPS      ETA       Delay
          │         │          │          │         │          │
          └─────────┼──────────┘          └─────────┼──────────┘
                    │                               │
                    ▼                               ▼
              Transport Data                   Event Stream
                    │                               │
                    └───────────────┬───────────────┘
                                    ▼
                             Ranking Engine
                                    │
                                    ▼
                              Configuration
```

---

# 5. Core Architectural Principle

The most important separation is:

```text
TRANSPORT DATA
       ↓
ROUTING
       ↓
JOURNEY GENERATION
       ↓
CONSTRAINTS
       ↓
METRICS
       ↓
RANKING
       ↓
PRESENTATION
```

Real-time tracking is a parallel capability:

```text
GPS / Operator Data
       ↓
Location Ingestion
       ↓
Vehicle State
       ↓
ETA Prediction
       ↓
Journey / Tracking APIs
```

Do not put all of these responsibilities inside a single `BusService` or `TransportService`.

---

# 6. Transport Modes

Define:

```java
public enum TransportMode {

    BUS,
    TRAIN,
    METRO,
    TRAM,
    WALK,
    BIKE,
    TAXI
}
```

Initially implement:

```text
BUS
```

Later:

```text
TRAIN
METRO
```

and eventually:

```text
MULTIMODAL
```

---

# 7. Common Transport Model

The system should have common concepts.

```text
TransportOperator
TransportStop
TransportRoute
TransportTrip
StopTime
Journey
JourneyLeg
Transfer
Fare
Vehicle
```

Different transport modes can provide specialized behavior.

---

# 8. Stop / Station

A common abstraction:

```text
TransportStop
```

contains:

```text
id
name
latitude
longitude
type
zone
status
```

Possible types:

```text
BUS_STOP
RAILWAY_STATION
METRO_STATION
TRAM_STOP
```

Example:

```text
Ameerpet
17.4375
78.4483
METRO_STATION
```

---

# 9. Route

A route represents the logical path of a transport service.

Example:

```text
Route 10A

A → B → C → D → E
```

Route data:

```text
routeId
routeNumber
name
operatorId
transportMode
direction
status
```

A route describes:

> Where the service travels.

It does not describe a specific departure.

---

# 10. Route Stops

A route contains ordered stops.

```text
RouteStop

routeId
stopId
sequence
distanceFromStart
```

Example:

```text
Route 10A

Sequence     Stop
-------------------------
1            A
2            B
3            C
4            D
5            E
```

The sequence allows the system to determine whether:

```text
A → D
```

is possible.

If:

```text
sequence(A) < sequence(D)
```

the route can travel from A to D in that direction.

---

# 11. Train-Specific Route

A train can use the same conceptual model.

```text
Train Route

Secunderabad
      ↓
Kazipet
      ↓
Warangal
      ↓
Vijayawada
      ↓
Chennai
```

The difference is that railway infrastructure may introduce additional concepts:

```text
Track
Platform
RailwaySegment
RailwayStation
RailwayZone
```

These can remain inside the train-specific module.

---

# 12. Trip

A route describes the path.

A trip describes a particular scheduled service.

Example:

```text
Route 10A

A → B → C → D
```

Trips:

```text
Trip 001
08:00

Trip 002
08:30

Trip 003
09:00

Trip 004
09:30
```

Therefore:

```text
Route ≠ Trip
```

This distinction is critical.

---

# 13. Stop Time

Every trip has arrival/departure information.

```text
StopTime

tripId
stopId
sequence
arrivalTime
departureTime
```

Example:

```text
Trip 001

Stop       Arrival    Departure
--------------------------------
A          08:00      08:02
B          08:10      08:11
C          08:20      08:21
D          08:35      08:36
```

The same concept works for trains:

```text
Train 12701

Station       Arrival    Departure
------------------------------------
Secunderabad  --         10:00
Kazipet       11:30      11:35
Warangal      12:10      12:12
Vijayawada    14:00      14:05
Chennai       19:00      --
```

---

# 14. Service Calendar

A trip may operate only on certain days.

Create:

```text
ServiceCalendar
```

Example:

```text
Monday      YES
Tuesday     YES
Wednesday   YES
Thursday    YES
Friday      YES
Saturday    NO
Sunday      NO
```

Also support exceptions:

```text
HOLIDAY
SPECIAL_SERVICE
CANCELLED_SERVICE
```

---

# 15. Vehicle

A physical vehicle is different from a scheduled trip.

```text
Vehicle

id
registrationNumber
operatorId
transportMode
type
capacity
status
```

Bus:

```text
BUS-1001
```

Train:

```text
TRAIN-12701
```

Metro:

```text
METRO-5001
```

---

# 16. Trip vs Vehicle

Do not combine:

```text
Trip
```

and:

```text
Vehicle
```

A scheduled trip is:

> A planned transportation service.

A vehicle is:

> The physical object performing that service.

Example:

```text
Trip 12701
      ↓
Train 12701
      ↓
Physical locomotive + coaches
```

This separation becomes essential for real-time tracking.

---

# 17. Search Request

A search request should support more than source and destination.

```json
{
  "source": {
    "latitude": 17.4375,
    "longitude": 78.4483
  },
  "destination": {
    "latitude": 17.3850,
    "longitude": 78.4867
  },
  "travelDate": "2026-09-20",
  "departureTime": "10:00",
  "preferences": {
    "maximumTransfers": 2,
    "maximumWalkingDistanceMeters": 1000,
    "maximumFare": 100
  },
  "transportModes": [
    "BUS",
    "TRAIN",
    "METRO"
  ],
  "rankingProfile": "BALANCED"
}
```

---

# 18. Source and Destination Resolution

The user may provide:

```text
Exact stop
Station
Address
Place name
Latitude/longitude
Current GPS location
```

Therefore create:

```text
LocationResolver
```

Flow:

```text
User Location
      ↓
Geospatial Search
      ↓
Nearby Transport Stops
      ↓
Candidate Source Stops
```

For destination:

```text
Destination
      ↓
Geospatial Search
      ↓
Candidate Destination Stops
```

---

# 19. Geospatial Index

Do not calculate distance against every stop.

Possible technologies:

```text
PostGIS
H3
Geohash
R-Tree
KD-Tree
S2
```

Example:

```text
GPS
 ↓
H3 Cell
 ↓
Nearby Cells
 ↓
Candidate Stops
 ↓
Exact Distance
```

This provides an efficient two-stage search:

```text
Approximate Index Filter
        ↓
Exact Distance Calculation
```

---

# 20. Direct Journey Search

Example:

```text
Source = A
Destination = D
```

Candidate routes:

```text
Route 10A
A → B → C → D

Route 20B
A → X → D

Route 50C
A → B → D
```

For each route:

```text
Find source sequence
Find destination sequence
```

Then:

```text
sourceSequence < destinationSequence
```

means the route can serve the journey.

---

# 21. Find Candidate Trips

Once candidate routes are found:

```text
Route
  ↓
Trips
  ↓
Service Calendar
  ↓
Requested Date
  ↓
Requested Time
```

Example:

```text
User departure:
10:00
```

Candidate trips:

```text
10:05
10:20
10:35
11:00
```

---

# 22. Journey

The search engine should return a domain-level:

```text
Journey
```

rather than database entities.

Example:

```text
Journey

departureTime
arrivalTime
duration
fare
walkingDistance
waitingTime
numberOfTransfers
legs[]
```

---

# 23. Journey Leg

A journey consists of one or more legs.

Direct journey:

```text
Journey
   │
   └── Leg
        BUS
        A → D
```

Transfer journey:

```text
Journey
   │
   ├── Leg 1
   │     BUS
   │     A → C
   │
   ├── Transfer
   │
   └── Leg 2
         BUS
         C → D
```

Multimodal journey:

```text
Journey
   │
   ├── WALK
   ├── METRO
   ├── WALK
   └── TRAIN
```

---

# 24. Transfer

Create:

```text
Transfer

fromStop
toStop
walkingDistance
walkingTime
minimumTransferTime
```

Example:

```text
Bus arrives:
10:30

Train departs:
10:38

Transfer time:
8 minutes

Required:
5 minutes

Result:
VALID
```

---

# 25. Transfer Constraints

Configuration:

```yaml
transfer:
  minimumTransferMinutes: 5
  maximumTransferMinutes: 30
  maximumTransfers: 2
```

The values should be configurable.

---

# 26. Graph Model

Represent the transportation network as a graph.

```text
Stop / Station = Node

Transport connection = Edge
```

Example:

```text
A ─── B ─── C
     │       │
     D ───── E
```

An edge can contain:

```text
departureTime
arrivalTime
travelTime
route
trip
fare
transportMode
```

---

# 27. Time-Dependent Graph

Transportation networks are not static graphs.

The available edge depends on time.

Example:

```text
A → B

Bus 1:
10:00 → 10:20

Bus 2:
10:15 → 10:25
```

If the user reaches A at:

```text
10:05
```

Bus 1 cannot be used.

The routing engine must select:

```text
Bus 2
```

Therefore the system requires **time-dependent routing**.

---

# 28. Routing Algorithms

The routing layer should expose an abstraction:

```java
public interface RoutingAlgorithm {

    List<Journey> findJourneys(
        RoutingRequest request
    );
}
```

Possible implementations:

```text
DirectRoutingAlgorithm
DijkstraRoutingAlgorithm
TimeDependentDijkstra
AStarRoutingAlgorithm
RaptorRoutingAlgorithm
ConnectionScanAlgorithm
MultiModalRoutingAlgorithm
```

Initially:

```text
Direct search
+
One-transfer search
```

Then evolve toward:

```text
Time-dependent routing
+
RAPTOR / CSA
```

for larger public-transport networks.

---

# 29. Strategy Pattern for Routing

Use:

```text
RoutingAlgorithm
```

as a Strategy.

Configuration:

```yaml
routing:
  algorithm: TIME_DEPENDENT_DIJKSTRA
```

Later:

```yaml
routing:
  algorithm: RAPTOR
```

The rest of the system should not care which algorithm is used.

---

# 30. Journey Constraints

Candidate journeys should pass through a constraint engine.

Possible constraints:

```text
Maximum Fare
Maximum Transfers
Maximum Walking Distance
Maximum Journey Duration
Departure Window
Arrival Window
Transport Mode
Operator
Accessibility
```

---

# 31. Specification Pattern

Each constraint can be independent.

```text
MaximumFareSpecification

MaximumTransferSpecification

MaximumWalkingSpecification

MaximumDurationSpecification
```

They can be combined:

```text
MaximumFare
       AND
MaximumTransfers
       AND
MaximumWalkingDistance
```

This prevents a huge conditional block inside the search service.

---

# 32. Journey Metrics

Before ranking, calculate:

```text
Total Duration
Travel Time
Waiting Time
Walking Time
Walking Distance
Fare
Number of Transfers
Reliability
Crowding
```

Create:

```text
JourneyMetrics
```

Example:

```json
{
  "durationMinutes": 42,
  "travelMinutes": 35,
  "waitingMinutes": 5,
  "walkingMinutes": 2,
  "walkingDistanceMeters": 180,
  "fare": 30,
  "transfers": 0
}
```

---

# 33. Ranking Engine

The system should not hardcode:

```java
if (duration < ...)
```

Instead:

```java
public interface RankingCriterion {

    double calculateScore(
        Journey journey,
        RankingContext context
    );
}
```

Implement:

```text
TravelTimeCriterion
FareCriterion
TransferCriterion
WalkingDistanceCriterion
WaitingTimeCriterion
ReliabilityCriterion
CrowdingCriterion
```

---

# 34. Weighted Ranking

Example:

```yaml
ranking:
  profile: BALANCED

  criteria:

    travelTime:
      enabled: true
      weight: 0.40

    transfers:
      enabled: true
      weight: 0.25

    fare:
      enabled: true
      weight: 0.20

    walkingDistance:
      enabled: true
      weight: 0.10

    waitingTime:
      enabled: true
      weight: 0.05
```

Final score:

```text
Travel Time Score × 0.40
+
Transfer Score × 0.25
+
Fare Score × 0.20
+
Walking Score × 0.10
+
Waiting Score × 0.05
```

---

# 35. Score Normalization

Different criteria have different units.

Example:

```text
40 minutes
₹30
500 meters
1 transfer
```

Normalize each criterion into a common scale:

```text
0 → worst
1 → best
```

Then apply weights.

---

# 36. Ranking Profiles

Create reusable profiles:

```text
FASTEST
CHEAPEST
FEWEST_TRANSFERS
LEAST_WALKING
BALANCED
CUSTOM
```

Example:

```yaml
profiles:

  fastest:
    travelTime: 0.70
    waitingTime: 0.15
    transfers: 0.10
    walkingDistance: 0.05

  cheapest:
    fare: 0.70
    travelTime: 0.15
    transfers: 0.10
    walkingDistance: 0.05
```

---

# 37. User Preferences

System configuration and user preferences should be separate.

Example:

```text
System Configuration
        +
User Preferences
        ↓
Effective Ranking Configuration
```

One user may prefer:

```text
Fastest
```

Another:

```text
Cheapest
```

Another:

```text
Least walking
```

---

# 38. Pareto Optimization

Weighted ranking is useful, but some journeys may have trade-offs.

Example:

```text
Journey A
30 min
₹50
2 transfers

Journey B
40 min
₹20
0 transfers
```

Instead of forcing every journey into a single score, the system can optionally identify **Pareto-optimal journeys**.

This allows the UI to present genuinely different alternatives.

---

# 39. Result Categories

Instead of showing 100 similar journeys, group useful alternatives:

```text
Fastest

Cheapest

Fewest Transfers

Least Walking

Earliest Arrival
```

Example:

```text
Fastest
10:00 → 10:35

Cheapest
10:10 → 11:00

Fewest Transfers
10:05 → 10:45

Least Walking
10:15 → 10:50
```

---

# 40. Search API

```http
POST /api/v1/journeys/search
```

Request:

```json
{
  "source": {
    "latitude": 17.4375,
    "longitude": 78.4483
  },
  "destination": {
    "latitude": 17.3850,
    "longitude": 78.4867
  },
  "date": "2026-09-20",
  "departureTime": "10:00",
  "transportModes": [
    "BUS",
    "TRAIN",
    "METRO"
  ],
  "rankingProfile": "BALANCED"
}
```

---

# 41. Search Response

```json
{
  "searchId": "SEARCH-123",
  "journeys": [
    {
      "departureTime": "10:05",
      "arrivalTime": "10:45",
      "durationMinutes": 40,
      "fare": 30,
      "walkingDistanceMeters": 250,
      "transfers": 0,
      "legs": [
        {
          "transportMode": "BUS",
          "service": "10A",
          "from": "A",
          "to": "D"
        }
      ]
    }
  ]
}
```

---

# 42. Real-Time Tracking

Journey planning and real-time tracking are separate capabilities.

```text
Journey Planning:

"How can I travel?"

Tracking:

"Where is my vehicle?"
```

Architecture:

```text
GPS / Operator Data
        ↓
Location Ingestion
        ↓
Event Stream
        ↓
Vehicle State
        ↓
ETA Engine
        ↓
Tracking API
```

---

# 43. Real-Time Vehicle State

```text
VehicleState

vehicleId
tripId
latitude
longitude
speed
direction
currentStop
nextStop
delay
status
lastUpdated
```

Example:

```text
Train 12701

Current:
Kazipet

Next:
Warangal

Speed:
72 km/h

Delay:
15 minutes
```

---

# 44. Real-Time Events

Use events such as:

```text
VEHICLE_LOCATION_UPDATED

TRIP_STARTED

TRIP_DELAYED

TRIP_CANCELLED

VEHICLE_ARRIVED

VEHICLE_DEPARTED

STOP_CLOSED

ROUTE_CHANGED
```

---

# 45. Event-Driven Architecture

For large-scale real-time updates:

```text
GPS
 │
 ▼
Location Ingestion
 │
 ▼
Kafka
 │
 ├──────────────┐
 ▼              ▼
Vehicle State   ETA Engine
 │              │
 ▼              ▼
Tracking API   Search Engine
```

Kafka is useful because many consumers may need the same real-time event.

---

# 46. ETA Engine

Create:

```java
public interface EtaProvider {

    EstimatedArrival estimate(
        VehicleState vehicle,
        TransportStop stop
    );
}
```

Implementations:

```text
ScheduledEtaProvider
GpsBasedEtaProvider
HistoricalEtaProvider
TrafficAwareEtaProvider
RailwayOperationalEtaProvider
```

The implementation can be selected using configuration.

---

# 47. Delay Calculation

Compare:

```text
Scheduled Arrival
```

against:

```text
Predicted Arrival
```

Example:

```text
Scheduled:
14:20

Predicted:
14:35

Delay:
15 minutes
```

This can be exposed through:

```http
GET /api/v1/trains/{trainNumber}/status
```

or a generic:

```http
GET /api/v1/vehicles/{vehicleId}/status
```

---

# 48. "Where Is My Train?" Flow

```text
User enters train number
        ↓
Find today's TrainRun
        ↓
Get current VehicleState
        ↓
Determine current position
        ↓
Find current/next station
        ↓
Calculate ETA
        ↓
Calculate delay
        ↓
Return tracking information
```

Example response:

```text
Train 12701

Current Location:
Kazipet

Next Station:
Warangal

Expected Arrival:
12:25

Scheduled Arrival:
12:10

Delay:
15 minutes
```

---

# 49. "Where Is My Bus?" Uses the Same Architecture

```text
TrackingEngine
       │
       ├── BusTrackingProvider
       ├── TrainTrackingProvider
       └── MetroTrackingProvider
```

Therefore one tracking architecture can support:

```text
Where is my bus?

Where is my train?

Where is my metro?
```

---

# 50. Multimodal Journey

Eventually a user could search:

```text
Home → Chennai
```

and receive:

```text
WALK
 ↓
METRO
 ↓
WALK
 ↓
TRAIN
 ↓
WALK
```

Represent this as:

```text
Journey
   │
   ├── Walk Leg
   ├── Metro Leg
   ├── Walk Leg
   └── Train Leg
```

The same Journey model remains valid.

---

# 51. Multimodal Routing

```text
                    Journey Planner
                          │
        ┌─────────────────┼─────────────────┐
        ▼                 ▼                 ▼
     Bus Graph         Train Graph       Metro Graph
        │                 │                 │
        └─────────────────┼─────────────────┘
                          ▼
                 Multimodal Router
                          │
                          ▼
                       Journey
```

---

# 52. Fare Calculation

Fare should also be extensible.

```java
public interface FareCalculator {

    Money calculate(Journey journey);
}
```

Implementations:

```text
FlatFareCalculator
DistanceBasedFareCalculator
ZoneBasedFareCalculator
DynamicFareCalculator
MultiOperatorFareCalculator
```

---

# 53. Operator Integration

Different transport operators may expose different data formats.

Create:

```java
public interface OperatorAdapter {

    TransportData importData();
}
```

Possible implementations:

```text
OperatorApiAdapter
GtfsAdapter
CsvOperatorAdapter
RailwayDataAdapter
MetroDataAdapter
```

The adapter converts external data into the internal transport model.

---

# 54. GTFS-Compatible Design

For public transport integration, consider supporting concepts similar to **GTFS**:

```text
Agency
Stops
Routes
Trips
Stop Times
Calendar
Calendar Dates
Shapes
Fare
```

This makes it easier to import standardized public transport data.

---

# 55. Route Shape

A route may contain a geographical shape.

```text
Route
  ↓
Shape
  ↓
Polyline
```

For example:

```text
A ─────── B
          \
           C
            \
             D
```

Store geographical shape data separately from stop ordering.

This allows:

```text
Route visualization
Vehicle tracking
Distance calculation
Map rendering
```

---

# 56. Map Integration

Frontend:

```text
Map
 │
 ├── Stops
 ├── Routes
 ├── Vehicles
 └── User Location
```

Backend provides:

```text
Stop coordinates
Route geometry
Vehicle coordinates
Journey legs
```

The frontend can render the result using:

```text
OpenStreetMap
Google Maps
Mapbox
Leaflet
OpenLayers
```

without coupling the domain layer to a particular map provider.

---

# 57. Caching

Frequently accessed data can be cached.

Examples:

```text
Stop → Routes
Route → Stops
Route → Trips
Trip → StopTimes
Popular Searches
Configuration
Vehicle State
```

Redis can be introduced.

Example:

```text
route:10A:stops

trip:12701:stoptimes

vehicle:12701:state
```

---

# 58. Search Result Caching

Popular searches can be cached.

Cache key:

```text
source
destination
date
time
transportModes
rankingProfile
```

However, real-time searches require shorter TTLs or invalidation when:

```text
Delay changes
Trip cancelled
Route changed
Vehicle position changes
```

---

# 59. Database

A good initial database is:

```text
PostgreSQL
```

with geospatial support through:

```text
PostGIS
```

Core tables:

```text
operator
vehicle
stop
route
route_stop
trip
stop_time
service_calendar
service_exception
fare
route_shape
```

---

# 60. Important Indexes

Indexes should exist on frequently searched fields.

Examples:

```text
stop.id
route.id
route_stop.route_id
route_stop.stop_id
route_stop.sequence
trip.route_id
trip.service_id
stop_time.trip_id
stop_time.stop_id
stop_time.departure_time
```

Geospatial indexes:

```text
GIST(location)
```

when using PostGIS.

---

# 61. Search Optimization

Avoid:

```text
Load every route
        ↓
Load every trip
        ↓
Compare everything
```

Instead:

```text
User Location
      ↓
Nearby Stops
      ↓
Candidate Routes
      ↓
Candidate Trips
      ↓
Journey Generation
      ↓
Constraint Filtering
      ↓
Ranking
```

This dramatically reduces the search space.

---

# 62. Precomputed Relationships

Useful derived indexes:

```text
Stop → Routes

Route → Stops

Stop → Upcoming Trips

Stop Pair → Direct Routes
```

For very popular queries, carefully selected precomputation can improve latency.

Do not precompute every possible source-destination combination blindly.

---

# 63. Top-K Search

Users normally need:

```text
Top 5
Top 10
Top 20
```

rather than thousands of journeys.

Use:

```text
Top-K selection
```

and avoid unnecessarily sorting huge candidate sets.

---

# 64. Pagination

For additional results:

```http
GET /api/v1/journeys?limit=20&cursor=abc
```

Cursor-based pagination is generally preferable for large or changing result sets.

---

# 65. Configuration Service

Centralize business configuration.

Example:

```yaml
search:
  maximumTransfers: 2
  maximumWalkingDistanceMeters: 1000
  maximumResults: 20

transfer:
  minimumMinutes: 5
  maximumMinutes: 30

routing:
  algorithm: TIME_DEPENDENT_DIJKSTRA

ranking:
  profile: BALANCED
```

Configuration may eventually be stored in:

```text
Database
Config Server
Feature Flag Platform
```

---

# 66. Feature Flags

New functionality can be released independently.

Example:

```yaml
features:
  realTimeEta: true
  multimodalRouting: false
  dynamicFare: false
  mlRanking: false
  crowdPrediction: false
```

This allows gradual rollout.

---

# 67. SOLID — Single Responsibility

Avoid a class like:

```text
BusService
```

doing:

```text
Database queries
Route matching
Trip search
Transfer calculation
Fare calculation
Ranking
Response mapping
```

Instead:

```text
StopResolver
RouteMatcher
TripFinder
JourneyPlanner
JourneyFilter
FareCalculator
RankingEngine
EtaProvider
ResponseMapper
```

Each has a focused responsibility.

---

# 68. SOLID — Open/Closed

Adding:

```text
CrowdingCriterion
```

should not require modifying:

```text
RankingEngine
```

Adding:

```text
RaptorRoutingAlgorithm
```

should not require rewriting:

```text
JourneySearchService
```

Adding:

```text
Train
```

should not require rewriting:

```text
Bus
```

---

# 69. SOLID — Dependency Inversion

Domain code should depend on interfaces:

```java
public interface StopRepository {

    Optional<Stop> findById(Long id);
}
```

Infrastructure:

```text
PostgresStopRepository
```

implements it.

The domain should not directly depend on:

```text
PostgreSQL
Redis
Kafka
Google Maps
```

---

# 70. Strategy Pattern

Excellent candidates:

```text
RoutingAlgorithm
RankingCriterion
FareCalculator
EtaProvider
LocationResolver
```

Example:

```text
RoutingAlgorithm
      │
      ├── Dijkstra
      ├── A*
      └── RAPTOR
```

---

# 71. Factory Pattern

Use factories when the implementation is selected dynamically.

```text
RoutingAlgorithmFactory
```

Configuration:

```text
routing.algorithm = RAPTOR
```

Factory:

```text
RaptorRoutingAlgorithm
```

The rest of the application remains independent of the concrete implementation.

---

# 72. Specification Pattern

Use specifications for constraints:

```text
MaximumFareSpecification
MaximumTransferSpecification
MaximumWalkingSpecification
TransportModeSpecification
AccessibilitySpecification
```

They can be composed.

---

# 73. Chain of Responsibility

Journey filtering can be modeled as:

```text
Candidate Journey
       ↓
Time Filter
       ↓
Fare Filter
       ↓
Transfer Filter
       ↓
Walking Filter
       ↓
Accessibility Filter
       ↓
Valid Journey
```

Each filter can be independently enabled or disabled.

---

# 74. Recommended Spring Boot Structure

```text
com.example.transport
│
├── common
│   ├── domain
│   ├── exception
│   └── configuration
│
├── location
│   ├── domain
│   ├── application
│   ├── infrastructure
│   └── api
│
├── transport
│   ├── bus
│   ├── train
│   └── metro
│
├── network
│
├── route
│
├── schedule
│
├── trip
│
├── journey
│
├── routing
│
├── ranking
│
├── fare
│
├── realtime
│
├── tracking
│
├── eta
│
└── api
```

---

# 75. Domain Layer

Contains business concepts:

```text
Stop
Route
RouteStop
Trip
StopTime
Journey
JourneyLeg
Transfer
Vehicle
Fare
```

Avoid coupling these objects directly to:

```text
HTTP
JPA
Kafka
Redis
```

where practical.

---

# 76. Application Layer

Contains use cases:

```text
SearchJourneyUseCase
TrackVehicleUseCase
FindNearbyStopsUseCase
CalculateFareUseCase
GetVehicleEtaUseCase
```

Example:

```text
SearchJourneyUseCase
        ↓
LocationResolver
        ↓
RoutingEngine
        ↓
JourneyBuilder
        ↓
ConstraintEngine
        ↓
RankingEngine
```

---

# 77. Infrastructure Layer

Contains:

```text
JPA
PostgreSQL
PostGIS
Redis
Kafka
External APIs
GTFS Importer
GPS Integration
Operator APIs
```

The application/domain layer should depend on abstractions rather than these implementations.

---

# 78. Real-Time Architecture

For high-scale systems:

```text
                         GPS Devices
                              │
                              ▼
                     Location Ingestion
                              │
                              ▼
                            Kafka
                              │
              ┌───────────────┼───────────────┐
              ▼               ▼               ▼
        Vehicle State     ETA Engine      Analytics
              │               │
              ▼               ▼
        Tracking API      Search Engine
              │               │
              └───────┬───────┘
                      ▼
                    Client
```

---

# 79. Why Kafka?

Many components may need the same event.

For:

```text
VEHICLE_LOCATION_UPDATED
```

consumers might include:

```text
Vehicle State Service
ETA Service
Tracking Service
Analytics Service
Alert Service
```

An event-stream architecture prevents the GPS producer from having to call every service synchronously.

---

# 80. Fault Tolerance

If real-time data becomes unavailable:

```text
Real-Time ETA
      ↓ failure
Scheduled ETA
```

The application should continue operating with degraded functionality.

Similarly:

```text
External Operator API
       ↓ failure
Cached Schedule
```

can be used when appropriate.

---

# 81. Observability

Every search should have:

```text
searchId
correlationId
```

Track:

```text
Search latency
P50
P95
P99
Candidate routes
Candidate trips
Generated journeys
Filtered journeys
Ranking latency
Cache hit/miss
Database latency
```

Example:

```text
SEARCH-123

Total:             240ms
Location:           20ms
Route Search:       30ms
Trip Search:        80ms
Journey Generation: 60ms
Ranking:            20ms
Response:           30ms
```

---

# 82. Distributed Tracing

Use OpenTelemetry or an equivalent tracing solution.

Example:

```text
API Gateway
      ↓
Journey Search
      ↓
Location Service
      ↓
Route Service
      ↓
Trip Service
      ↓
Routing Engine
      ↓
Ranking Engine
```

This helps identify bottlenecks in distributed deployments.

---

# 83. Security

APIs should support:

```text
HTTPS
Authentication
Authorization
Rate Limiting
Input Validation
API Gateway
Audit Logging
```

Admin roles:

```text
USER
OPERATOR
ADMIN
SUPER_ADMIN
```

Operators should only be able to manage their own transport data unless explicitly authorized otherwise.

---

# 84. Rate Limiting

Search can be computationally expensive.

Example configuration:

```yaml
rateLimit:
  anonymous:
    requestsPerMinute: 60

  authenticated:
    requestsPerMinute: 300
```

These should remain configurable.

---

# 85. Testing Strategy

### Unit Tests

Test:

```text
Route matching
Trip selection
Transfer calculation
Fare calculation
Ranking
Constraints
ETA
```

### Integration Tests

Test:

```text
PostgreSQL
PostGIS
Redis
Kafka
External adapters
```

### End-to-End Tests

Test:

```text
Search API
       ↓
Journey Planning
       ↓
Ranking
       ↓
Response
```

### Performance Tests

Test:

```text
100 RPS
500 RPS
1,000 RPS
5,000 RPS
```

Measure:

```text
P50
P95
P99
```

---

# 86. Recommended Implementation Roadmap

Do not implement the entire platform at once.

Build incrementally.

---

## Phase 1 — Transport Data Model

Implement:

```text
Stop
Route
RouteStop
Trip
StopTime
ServiceCalendar
```

Database:

```text
PostgreSQL
```

---

## Phase 2 — Direct Bus Search

Implement:

```text
Source
Destination
Date
Time
```

Flow:

```text
Source
 ↓
Routes
 ↓
Destination
 ↓
Trips
```

Return direct bus journeys.

---

## Phase 3 — Journey Domain Model

Implement:

```text
Journey
JourneyLeg
JourneyMetrics
```

Separate domain objects from database entities.

---

## Phase 4 — Ranking Engine

Implement:

```text
RankingEngine

TravelTimeCriterion
FareCriterion
TransferCriterion
WalkingDistanceCriterion
WaitingTimeCriterion
```

---

## Phase 5 — Configuration

Move:

```text
Maximum transfers
Maximum walking
Maximum results
Ranking weights
```

into configuration.

---

## Phase 6 — Transfer Search

Implement:

```text
Transfer
MultiLegJourney
```

Initially support:

```text
Maximum 1 transfer
```

Then:

```text
2 transfers
```

and eventually configurable multi-transfer routing.

---

## Phase 7 — Graph Routing

Introduce:

```text
Graph
Nodes
Edges
Time-dependent routing
```

Start with:

```text
Dijkstra
```

Then evaluate:

```text
A*
RAPTOR
CSA
```

based on network size and performance requirements.

---

## Phase 8 — Geospatial Search

Introduce:

```text
PostGIS
H3
Geohash
```

Support:

```text
"Find buses near me"
"Find stations near me"
```

---

## Phase 9 — Train

Add:

```text
Train
RailwayStation
TrainRoute
TrainRun
Platform
RailwaySegment
```

Reuse:

```text
Journey
Routing
Ranking
Configuration
Tracking
```

---

## Phase 10 — Metro

Add:

```text
Metro
MetroStation
MetroLine
MetroTrip
```

Again reuse the common journey architecture.

---

## Phase 11 — Real-Time Tracking

Add:

```text
VehicleLocation
VehicleState
GPS ingestion
Kafka
ETA Engine
Delay Engine
```

Support:

```text
Where is my bus?
Where is my train?
Where is my metro?
```

---

## Phase 12 — Redis

Cache:

```text
Stops
Routes
Trips
StopTimes
Vehicle State
Configuration
Popular searches
```

---

## Phase 13 — Multimodal Journey

Support:

```text
WALK
BUS
METRO
TRAIN
```

Example:

```text
Walk
 ↓
Metro
 ↓
Walk
 ↓
Train
 ↓
Walk
```

---

## Phase 14 — Advanced Routing

Evaluate:

```text
RAPTOR
Connection Scan Algorithm
A*
Multi-Criteria Routing
Pareto Optimization
```

based on actual performance requirements.

---

## Phase 15 — Advanced Personalization

Add:

```text
User preferences
Historical choices
Favorite routes
Favorite operators
Preferred transport modes
```

Eventually:

```text
Machine Learning Ranking
```

---

# 87. Future Enhancements

The architecture can eventually support:

```text
✓ Real-time GPS
✓ Real-time ETA
✓ Delay prediction
✓ Route disruption
✓ Station closure
✓ Bus crowd prediction
✓ Train platform information
✓ Seat availability
✓ Online ticket booking
✓ QR tickets
✓ Dynamic pricing
✓ Personalized ranking
✓ AI/ML ranking
✓ Multimodal routing
✓ Accessibility-aware routing
✓ Carbon-aware routing
✓ Traffic-aware routing
✓ Weather-aware routing
✓ Event-aware routing
✓ Route recommendations
✓ Journey history
✓ Push notifications
✓ Operator dashboard
✓ Driver application
```

---

# 88. Advanced AI/ML Architecture

AI should be added after the rule-based system is stable.

Collect events such as:

```text
Search
Journey Viewed
Journey Selected
Journey Cancelled
Actual Travel
User Preference
```

Then create:

```text
Training Data
      ↓
ML Ranking Model
      ↓
MLRankingStrategy
```

The existing interface remains:

```java
RankingEngine
```

Therefore:

```text
RuleBasedRankingStrategy
```

can coexist with:

```text
MLRankingStrategy
```

---

# 89. Final Architecture

```text
                         USER
                           │
                           ▼
                    Web / Mobile
                           │
                           ▼
                     API Gateway
                           │
            ┌──────────────┴──────────────┐
            │                             │
            ▼                             ▼
     Journey Search                  Tracking API
            │                             │
            ▼                             ▼
     Location Resolver              Vehicle State
            │                             │
            ▼                             ▼
     Nearby Stops                     ETA Engine
            │                             │
            ▼                             ▼
      Routing Engine                 Delay Engine
            │                             │
      ┌─────┼─────┐                       │
      ▼     ▼     ▼                       │
     Bus  Train  Metro                    │
      │     │     │                       │
      └─────┼─────┘                       │
            ▼                             │
      Journey Builder                     │
            │                             │
            ▼                             │
      Constraint Engine                   │
            │                             │
            ▼                             │
      Metrics Calculator                  │
            │                             │
            ▼                             │
       Ranking Engine                     │
            │                             │
            └──────────────┬──────────────┘
                           ▼
                       Results


REAL-TIME PIPELINE

GPS / Operator
      │
      ▼
Location Ingestion
      │
      ▼
Kafka
      │
      ├──────────────┬──────────────┐
      ▼              ▼              ▼
Vehicle State      ETA          Analytics
      │              │
      └───────┬──────┘
              ▼
        Tracking/Search
```

---

# 90. Final Domain Dependency Direction

The dependency direction should be:

```text
API
 ↓
APPLICATION
 ↓
DOMAIN
 ↑
INFRASTRUCTURE
```

Infrastructure implements interfaces defined by the application/domain layer.

For example:

```text
Domain
  │
  └── StopRepository
          ▲
          │
          └── PostgresStopRepository
```

The domain does not know that PostgreSQL exists.

---

# 91. Most Important Design Rules

### Rule 1

Do not make the core system bus-specific.

Use:

```text
Transport
```

and:

```text
TransportMode
```

---

### Rule 2

Separate:

```text
Route
Trip
Vehicle
```

They represent different concepts.

---

### Rule 3

Separate:

```text
Journey Planning
```

from:

```text
Real-Time Tracking
```

---

### Rule 4

Separate:

```text
Routing
```

from:

```text
Ranking
```

Routing answers:

> Which journeys are possible?

Ranking answers:

> How should the possible journeys be ordered?

---

### Rule 5

Separate:

```text
Constraints
```

from:

```text
Ranking
```

A maximum fare is a constraint.

"Prefer cheaper" is a ranking criterion.

---

### Rule 6

Keep business rules configurable.

Avoid:

```java
if (maximumTransfers == 2)
```

Use configuration.

---

### Rule 7

Use interfaces around replaceable algorithms.

```text
RoutingAlgorithm
RankingCriterion
FareCalculator
EtaProvider
LocationResolver
```

---

### Rule 8

Optimize only after measuring.

Start with:

```text
PostgreSQL
Direct Search
Simple Routing
```

Then introduce:

```text
Redis
PostGIS
H3
Kafka
Read Replicas
Graph Index
```

as actual scale requires.

---

# 92. Final Mental Model

The entire platform can be understood as five major engines:

```text
┌─────────────────────────────────────────┐
│           TRANSPORT DATA                │
│                                         │
│ Stops • Routes • Trips • Vehicles       │
└───────────────────┬─────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────┐
│           ROUTING ENGINE                │
│                                         │
│ Which journeys are possible?            │
└───────────────────┬─────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────┐
│          CONSTRAINT ENGINE              │
│                                         │
│ Which journeys are acceptable?          │
└───────────────────┬─────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────┐
│           RANKING ENGINE                │
│                                         │
│ Which acceptable journeys are preferred │
│ according to configured criteria?       │
└───────────────────┬─────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────┐
│             USER RESULT                 │
│                                         │
│ Fastest • Cheapest • Least Walking      │
│ Fewest Transfers • Personalized         │
└─────────────────────────────────────────┘


PARALLEL:

GPS / OPERATOR
      ↓
REAL-TIME ENGINE
      ↓
LOCATION + ETA + DELAY
      ↓
JOURNEY / TRACKING RESULT
```

---

# 93. End Goal

The final platform should not be thought of as:

```text
Bus Search Application
```

but as:

```text
             PUBLIC TRANSPORT PLATFORM

                         │
       ┌─────────────────┼─────────────────┐
       │                 │                 │
      BUS              TRAIN             METRO
       │                 │                 │
       └─────────────────┼─────────────────┘
                         │
                Common Transport Model
                         │
          ┌──────────────┼──────────────┐
          ▼              ▼              ▼
       Routing        Ranking        Tracking
          │              │              │
          └──────────────┼──────────────┘
                         ▼
                   Journey Engine
                         │
                         ▼
                  User Applications
```

The architectural objective is:

> **Adding a new transport mode should not require rewriting the routing, ranking, journey or tracking architecture. Adding a new ranking criterion should not require modifying the routing engine. Replacing the routing algorithm should not require modifying the API. Replacing PostgreSQL, Redis, Kafka or an external GPS provider should not require modifying the domain logic.**

That is the core principle that makes the system **SOLID, scalable, configurable and extensible**.