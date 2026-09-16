# Scalable Online Bus Route Search & Matching System

## 1. Problem Statement

Design a scalable and configurable **Online Bus Route Search & Matching System** where users can search for journeys between a source and destination.

The system should:

- Support multiple bus operators.
- Support multiple buses.
- Support multiple routes.
- Support multiple stops per route.
- Support the same stop being part of multiple routes.
- Support different schedules for the same route.
- Support buses traveling in different directions.
- Support direct journeys.
- Support journeys requiring one or more transfers.
- Allow users to specify:
  - Source
  - Destination
  - Date
  - Preferred departure time
  - Preferred arrival time
  - Maximum walking distance
  - Maximum number of transfers
  - Preferred bus/operator
  - Maximum fare
  - Other configurable criteria.
- Return multiple possible journeys.
- Rank journeys according to configurable criteria.
- Allow new ranking criteria without modifying existing core logic.
- Scale to millions of stops, routes, trips and daily searches.
- Follow SOLID principles.
- Use extensible design patterns.
- Separate business rules from infrastructure.
- Support future real-time bus tracking.

---

# 2. Refined System-Design Question

A good interview/system-design question would be:

> **Design a scalable and configurable public bus journey planning system that allows users to search for journeys between any two locations/stops and returns all feasible direct and multi-leg bus journeys, ranked according to configurable criteria such as travel time, transfers, fare, walking distance, waiting time and user preferences.**

### Functional requirements

The system should support:

```text
Source
Destination
Date
Departure Time / Arrival Time
```

and return:

```text
Journey 1
    Bus A
    Stop A → Stop D
    Departure: 10:00
    Arrival: 10:40
    Fare: ₹30

Journey 2
    Bus B
    Stop A → Stop B
    Transfer
    Bus C
    Stop B → Stop D
    Departure: 10:15
    Arrival: 11:00
    Fare: ₹25
```

The system should rank these journeys.

---

# 3. Non-Functional Requirements

The system should be:

### Scalable

It should support:

```text
Millions of stops
Thousands of routes
Millions of scheduled trips
Millions of daily searches
```

### Configurable

Business rules should not be hardcoded.

For example:

```text
Maximum walking distance = 1 km

Maximum transfers = 2

Ranking:

Travel Time       = 40%
Transfers         = 25%
Fare              = 20%
Walking Distance  = 10%
Waiting Time      = 5%
```

These should be configurable.

### Extensible

The system should allow future criteria:

```text
Bus crowding
AC preference
Operator preference
Accessibility
Live traffic
Live GPS
Seat availability
Weather
User preferences
Surge pricing
Carbon emission
```

without rewriting the search engine.

---

# 4. High-Level Architecture

```text
                    ┌───────────────────────┐
                    │       Client          │
                    │ Web / Mobile / PWA    │
                    └───────────┬───────────┘
                                │
                                ▼
                    ┌───────────────────────┐
                    │      API Gateway      │
                    └───────────┬───────────┘
                                │
                                ▼
              ┌─────────────────────────────────┐
              │       Journey Search API         │
              └────────────────┬────────────────┘
                               │
              ┌────────────────┼────────────────┐
              ▼                ▼                ▼
       ┌─────────────┐ ┌──────────────┐ ┌──────────────┐
       │ Location    │ │ Route Search │ │ Trip Search  │
       │ Service     │ │ Engine       │ │ Engine       │
       └─────────────┘ └──────┬───────┘ └──────────────┘
                               │
                               ▼
                     ┌──────────────────┐
                     │ Journey Builder  │
                     └────────┬─────────┘
                              │
                              ▼
                     ┌──────────────────┐
                     │ Ranking Engine   │
                     └────────┬─────────┘
                              │
                              ▼
                     ┌──────────────────┐
                     │ Result Formatter │
                     └──────────────────┘
```

---

# 5. Core Domain Model

The first important decision is to distinguish:

```text
Stop
Route
Trip
Bus
Schedule
Journey
Leg
```

These are different concepts.

---

# 6. Stop

A `Stop` represents a physical bus stop.

```text
Stop
-----
id
name
latitude
longitude
address
zone
status
```

Example:

```text
STOP_1001

Name:
Ameerpet

Latitude:
17.4375

Longitude:
78.4483
```

---

# 7. Route

A route represents the logical path followed by a bus.

Example:

```text
Route 10A

A → B → C → D → E
```

Model:

```text
Route
-----
id
routeNumber
name
operatorId
direction
status
```

But the route itself should not contain schedule information.

---

# 8. Route Stops

A route can contain many stops.

```text
RouteStop
---------

routeId
stopId
sequence
distanceFromStart
```

Example:

```text
Route 10A

Sequence   Stop
--------------------
1          A
2          B
3          C
4          D
5          E
```

This sequence is extremely important.

It allows us to determine:

```text
Can bus travel from A → D?
```

because:

```text
sequence(A) < sequence(D)
```

---

# 9. Bus

A bus represents a physical vehicle.

```text
Bus
---
id
registrationNumber
operatorId
busType
capacity
accessibility
status
```

Example:

```text
Bus:

TS09AB1234

Type:
AC

Capacity:
45
```

---

# 10. Trip

This is one of the most important concepts.

A route describes:

> Where the bus travels.

A trip describes:

> When a specific service travels that route.

Example:

```text
Route:

10A
A → B → C → D → E
```

Trips:

```text
Trip 1
10A
08:00 → 09:00

Trip 2
10A
09:00 → 10:00

Trip 3
10A
10:00 → 11:00
```

Therefore:

```text
Route ≠ Trip
```

---

# 11. Stop Time

A trip needs arrival/departure information for every stop.

```text
TripStopTime
------------

tripId
stopId
sequence
arrivalTime
departureTime
```

Example:

```text
Trip 101

Stop       Arrival    Departure
---------------------------------
A          08:00      08:02
B          08:10      08:11
C          08:20      08:21
D          08:35      08:36
E          08:50      08:52
```

This enables precise journey planning.

---

# 12. Database Model

A relational database can initially contain:

```text
operator
bus
stop
route
route_stop
trip
trip_stop_time
fare
service_calendar
```

Example relationship:

```text
Operator
   │
   ├── Bus
   │
   └── Route
         │
         └── RouteStop
               │
               └── Stop

Route
  │
  └── Trip
        │
        └── TripStopTime
```

---

# 13. Why Route and Trip Must Be Separate

Suppose:

```text
Route 10A:

A → B → C → D → E
```

The same route may have:

```text
06:00
06:30
07:00
07:30
08:00
...
```

If schedule data is stored directly inside the route, the model becomes difficult to maintain.

Instead:

```text
Route
  ↓
Many Trips
  ↓
Many StopTimes
```

This separation makes the system extensible.

---

# 14. Search Request

A search request should not simply be:

```text
source
destination
```

Instead:

```json
{
  "source": {
    "stopId": "A"
  },
  "destination": {
    "stopId": "D"
  },
  "travelDate": "2026-09-20",
  "departureTime": "10:00",
  "preferences": {
    "maximumTransfers": 2,
    "maximumWalkingDistance": 1000,
    "maximumFare": 100
  }
}
```

---

# 15. Search Modes

The system should support several modes.

## Direct Bus

```text
A → B → C → D
```

User searches:

```text
A → D
```

Return:

```text
Bus 10A
A → D
```

---

## One Transfer

```text
A → B → C
          ↓
          D → E
```

Example:

```text
Bus 10A

A → B → C

Transfer at C

Bus 20B

C → D → E
```

---

## Multiple Transfers

```text
A
 ↓
Bus 10A
 ↓
B
 ↓
Bus 20B
 ↓
C
 ↓
Bus 30C
 ↓
D
```

Configuration can determine whether this is allowed.

---

# 16. Search Engine

The search engine can be divided into multiple stages.

```text
Search Request
      ↓
Validate Request
      ↓
Find Candidate Stops
      ↓
Find Candidate Routes
      ↓
Find Candidate Trips
      ↓
Build Direct Journeys
      ↓
Build Transfer Journeys
      ↓
Apply Constraints
      ↓
Calculate Journey Metrics
      ↓
Rank Journeys
      ↓
Return Results
```

---

# 17. Stage 1 — Validate Search

Validate:

```text
Source exists
Destination exists
Date valid
Time valid
Source != Destination
```

Also validate configuration:

```text
maximumTransfers >= 0

maximumWalkingDistance >= 0
```

---

# 18. Stage 2 — Source and Destination Resolution

The user may not select an exact bus stop.

They may search:

```text
Ameerpet
```

or:

```text
17.4375, 78.4483
```

Therefore introduce:

```text
Location Resolution Service
```

It can return:

```text
Nearest stops
```

Example:

```text
User Location
      ↓
Spatial Search
      ↓
Nearest Stops

Stop A - 150m
Stop B - 300m
Stop C - 650m
```

---

# 19. Spatial Index

For large systems, do not calculate distance against every stop.

Use:

```text
Geospatial Index
```

Possible implementations:

```text
PostGIS
Geohash
H3
S2
R-tree
KD-tree
```

For a very large system:

```text
GPS Location
      ↓
Spatial Index
      ↓
Nearby Stops
```

---

# 20. Stage 3 — Find Candidate Routes

Suppose:

```text
Source = A
Destination = D
```

Find routes containing both:

```text
Route 1: A B C D
Route 2: A X Y D
Route 3: A B D
```

These become candidate routes.

---

# 21. Route Matching

For every candidate route:

```text
sourceSequence
destinationSequence
```

If:

```text
sourceSequence < destinationSequence
```

then the route can potentially serve:

```text
source → destination
```

Example:

```text
A = 2
D = 5

2 < 5

Valid
```

Reverse direction:

```text
D = 5
A = 2

5 > 2

Invalid
```

unless a separate reverse-direction route exists.

---

# 22. Stage 4 — Find Candidate Trips

After finding candidate routes:

```text
Route
  ↓
Trips
```

Filter trips based on requested time.

Example:

```text
User departure:

10:00
```

Candidate trips:

```text
09:30
09:50
10:05
10:30
11:00
```

Depending on configuration, trips before 10:00 may be excluded.

---

# 23. Stage 5 — Build Direct Journey

Suppose:

```text
Trip 100

A 10:05
B 10:15
C 10:25
D 10:40
```

Journey:

```text
A → D

Departure:
10:05

Arrival:
10:40

Duration:
35 minutes
```

---

# 24. Journey Object

The search engine should not return raw database objects.

Create a domain object:

```text
Journey
-------

id

departureTime
arrivalTime

totalDuration

totalFare

walkingDistance

waitingTime

numberOfTransfers

legs[]
```

---

# 25. Journey Leg

A journey can contain multiple legs.

```text
Journey
   │
   ├── Leg 1
   │     Bus 10A
   │     A → C
   │
   ├── Transfer
   │
   └── Leg 2
         Bus 20B
         C → D
```

This model makes multi-modal transportation possible later.

---

# 26. Transfer Model

Create a separate concept:

```text
Transfer
--------

fromStop
toStop
walkingDistance
walkingTime
minimumTransferTime
```

Example:

```text
Bus 10A arrives:

10:30

Bus 20B departs:

10:38
```

If minimum transfer time is:

```text
5 minutes
```

then:

```text
8 minutes >= 5 minutes

Valid
```

---

# 27. Transfer Constraints

Transfer configuration can contain:

```text
minimumTransferTime
maximumTransferTime
maximumTransfers
maximumWalkingDistance
```

Example:

```json
{
  "minimumTransferTime": 5,
  "maximumTransferTime": 30,
  "maximumTransfers": 2
}
```

---

# 28. Multi-Leg Search

For multi-leg searches, model the transport network as a graph.

```text
Stop = Node

Bus connection = Edge
```

Example:

```text
A ---- B ---- C
      /        \
     D -------- E
```

Each edge can contain:

```text
travelTime
fare
route
trip
departureTime
arrivalTime
```

---

# 29. Graph Search

Possible algorithms include:

```text
BFS
Dijkstra
A*
Time-dependent Dijkstra
RAPTOR
CSA
Multi-criteria routing
```

For a realistic public-transport system, **time-dependent routing algorithms** are important because an edge's availability depends on the departure time.

For a first implementation:

```text
Direct search
        ↓
One-transfer search
        ↓
Multi-transfer graph search
```

is easier to build incrementally.

---

# 30. Why Normal Dijkstra Is Not Enough

Suppose:

```text
A → B

Bus 1
10:00 → 10:20

Bus 2
10:15 → 10:25
```

The cost of the edge depends on:

```text
Current time
```

Therefore transportation networks are **time-dependent graphs**.

The algorithm must consider:

```text
Current arrival time
        ↓
Next available bus
        ↓
Waiting time
        ↓
Travel time
```

---

# 31. Ranking Requirements

The system should not hardcode:

```java
if (duration < ...)
```

Instead create a ranking framework.

Possible criteria:

```text
Travel Time
Waiting Time
Number of Transfers
Fare
Walking Distance
Bus Comfort
Bus Type
Operator Preference
Accessibility
Reliability
Crowding
```

---

# 32. Ranking Strategy

Define:

```text
RankingCriterion
```

Example:

```java
interface RankingCriterion {

    double calculateScore(Journey journey);

}
```

Implement:

```text
TravelTimeCriterion
TransferCriterion
FareCriterion
WalkingDistanceCriterion
WaitingTimeCriterion
```

Each criterion is independent.

This follows:

```text
Single Responsibility Principle
Open/Closed Principle
Dependency Inversion Principle
```

---

# 33. Weighted Ranking

Example configuration:

```json
{
  "criteria": [
    {
      "name": "TRAVEL_TIME",
      "weight": 0.40
    },
    {
      "name": "TRANSFERS",
      "weight": 0.25
    },
    {
      "name": "FARE",
      "weight": 0.20
    },
    {
      "name": "WALKING_DISTANCE",
      "weight": 0.10
    },
    {
      "name": "WAITING_TIME",
      "weight": 0.05
    }
  ]
}
```

Then:

```text
Final Score =

Travel Score × 0.40
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

# 34. Normalize Scores

Different metrics have different units.

Example:

```text
Duration = 40 minutes
Fare = ₹30
Walking = 500 meters
Transfers = 1
```

They cannot directly be added.

Therefore normalize:

```text
0 → worst
1 → best
```

For example:

```text
Travel Score = normalized travel time
Fare Score = normalized fare
Walking Score = normalized walking distance
```

---

# 35. Ranking Pipeline

```text
Candidate Journeys
       ↓
Calculate Metrics
       ↓
Normalize Metrics
       ↓
Apply Criteria
       ↓
Calculate Weighted Score
       ↓
Sort
       ↓
Return Top N
```

---

# 36. Strategy Pattern

Ranking criteria are excellent candidates for the Strategy Pattern.

```java
public interface RankingStrategy {

    double score(Journey journey);

}
```

Implementations:

```java
public class FastestJourneyStrategy
        implements RankingStrategy {

    @Override
    public double score(Journey journey) {
        // calculate score
    }
}
```

But instead of creating one giant class, prefer independent criteria.

---

# 37. Composite Ranking

Create:

```java
public interface RankingEngine {

    List<Journey> rank(
        List<Journey> journeys,
        RankingConfiguration configuration
    );
}
```

Implementation:

```text
WeightedRankingEngine
```

Internally:

```text
RankingCriterion[]
```

This means adding:

```text
CrowdingCriterion
```

does not require modifying existing criteria.

---

# 38. Configuration-Driven Architecture

Do not hardcode:

```java
MAX_TRANSFERS = 2;
```

Instead:

```text
Configuration Service
```

stores:

```text
SEARCH_MAX_TRANSFERS

MAX_WALKING_DISTANCE

MIN_TRANSFER_TIME

MAX_RESULTS

RANKING_WEIGHTS
```

---

# 39. Configuration Example

```yaml
search:
  maximumTransfers: 2
  maximumWalkingDistanceMeters: 1000
  maximumResults: 20

transfer:
  minimumMinutes: 5
  maximumMinutes: 30

ranking:
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

---

# 40. User Preferences

System-level ranking and user-level preferences should be separate.

Example user:

```text
User prefers:

Cheapest
```

Another user:

```text
User prefers:

Fastest
```

Another:

```text
User prefers:

Least walking
```

Therefore:

```text
System Configuration
        +
User Preferences
        ↓
Effective Ranking Configuration
```

---

# 41. Search Modes

Instead of creating separate APIs:

```text
/fastest
/cheapest
/fewest-transfers
```

use:

```text
/search
```

with configuration:

```json
{
  "rankingProfile": "FASTEST"
}
```

Profiles:

```text
FASTEST
CHEAPEST
FEWEST_TRANSFERS
LEAST_WALKING
BALANCED
CUSTOM
```

---

# 42. Ranking Profile

Example:

```json
{
  "name": "FASTEST",
  "criteria": {
    "travelTime": 0.70,
    "waitingTime": 0.15,
    "transfers": 0.10,
    "walkingDistance": 0.05
  }
}
```

Another:

```json
{
  "name": "CHEAPEST",
  "criteria": {
    "fare": 0.70,
    "travelTime": 0.15,
    "transfers": 0.10,
    "walkingDistance": 0.05
  }
}
```

---

# 43. Pareto Optimization

Eventually, a weighted score may not be enough.

Consider:

```text
Journey A

30 minutes
₹50
2 transfers
```

```text
Journey B

40 minutes
₹20
0 transfers
```

Neither is objectively better in every dimension.

The system can support **Pareto-optimal journeys**.

A journey is dominated if another journey is:

```text
No slower
AND
No more expensive
AND
No more transfers
AND
No more walking
```

while being strictly better in at least one dimension.

This can reduce thousands of candidates to a meaningful set.

---

# 44. Result Grouping

Instead of returning 100 nearly identical results:

```text
Journey 1
Journey 2
Journey 3
...
Journey 100
```

group them:

```text
Fastest
10:00 → 10:40

Cheapest
10:15 → 11:00

Fewest Transfers
10:05 → 10:45

Least Walking
10:10 → 10:50
```

This gives users meaningful choices.

---

# 45. API Design

### Search API

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
  "rankingProfile": "BALANCED"
}
```

Response:

```json
{
  "searchId": "SEARCH-123",
  "journeys": [
    {
      "departureTime": "10:05",
      "arrivalTime": "10:45",
      "durationMinutes": 40,
      "fare": 30,
      "transfers": 0,
      "walkingDistanceMeters": 300,
      "legs": []
    }
  ]
}
```

---

# 46. API Versioning

Use:

```text
/api/v1
```

instead of:

```text
/api
```

Future:

```text
/api/v2
```

This allows backward-compatible evolution.

---

# 47. SOLID Architecture

Recommended layers:

```text
Controller
    ↓
Application Service
    ↓
Domain Service
    ↓
Repository Interface
    ↓
Infrastructure
```

Example:

```text
JourneySearchController
        ↓
JourneySearchApplicationService
        ↓
JourneySearchService
        ↓
RouteMatcher
        ↓
TripFinder
        ↓
JourneyBuilder
        ↓
RankingEngine
```

---

# 48. Single Responsibility Principle

Do not create:

```java
BusSearchService
```

that does everything.

Avoid:

```text
database query
route matching
trip matching
transfer calculation
ranking
response mapping
```

inside one class.

Instead:

```text
StopResolver
RouteMatcher
TripFinder
TransferFinder
JourneyBuilder
JourneyValidator
JourneyScorer
RankingEngine
JourneyResponseMapper
```

Each has one responsibility.

---

# 49. Open/Closed Principle

Suppose tomorrow we introduce:

```text
CrowdingScore
```

The existing ranking engine should not need major changes.

Add:

```java
CrowdingCriterion
```

and register it.

Existing code remains unchanged.

---

# 50. Liskov Substitution Principle

Implementations should respect contracts.

For example:

```java
RankingCriterion
```

must always behave like a ranking criterion.

Do not create one implementation that unexpectedly returns:

```text
null
```

or changes the meaning of the score.

---

# 51. Interface Segregation

Avoid:

```java
interface TransportService {

    searchBus();

    searchTrain();

    searchMetro();

    trackVehicle();

    calculateFare();

    reserveSeat();

}
```

Prefer smaller interfaces:

```text
RouteSearcher
TripSearcher
FareCalculator
VehicleTracker
SeatAvailabilityProvider
```

---

# 52. Dependency Inversion

Domain logic should not directly depend on:

```text
PostgreSQL
Redis
Kafka
Google Maps
```

Instead:

```java
public interface StopRepository {

    Optional<Stop> findById(Long id);

}
```

Infrastructure:

```java
PostgresStopRepository
```

implements it.

---

# 53. Domain Interfaces

Possible interfaces:

```text
StopRepository
RouteRepository
TripRepository
FareRepository

StopResolver
RouteMatcher
TripFinder
JourneyPlanner

FareCalculator
TransferCalculator

RankingCriterion
RankingEngine

RoutingAlgorithm
```

This makes the system highly replaceable.

---

# 54. Factory Pattern

Use a factory when selecting algorithms.

```text
RoutingAlgorithmFactory
```

Configuration:

```text
routingAlgorithm = RAPTOR
```

Factory returns:

```text
RaptorRoutingAlgorithm
```

Later:

```text
routingAlgorithm = CSA
```

returns:

```text
ConnectionScanAlgorithm
```

The rest of the application does not need to change.

---

# 55. Strategy Pattern

Excellent candidates:

```text
Routing algorithm
Fare calculation
Ranking
Transfer calculation
Location resolution
```

Example:

```text
RoutingStrategy

├── DirectRouteStrategy
├── GraphRoutingStrategy
├── RaptorRoutingStrategy
└── MultimodalRoutingStrategy
```

---

# 56. Specification Pattern

Search filters can use the Specification Pattern.

Examples:

```text
MaximumFareSpecification
MaximumTransferSpecification
MaximumWalkingSpecification
OperatorSpecification
AccessibilitySpecification
```

Combine:

```text
MaximumFare
AND
MaximumTransfers
AND
MaximumWalking
```

This keeps filtering modular.

---

# 57. Chain of Responsibility

Candidate journey processing can use:

```text
Validate
 ↓
Time Filter
 ↓
Transfer Filter
 ↓
Fare Filter
 ↓
Walking Filter
 ↓
Accessibility Filter
```

Each handler can reject invalid journeys.

---

# 58. Event-Driven Architecture

For real-time updates:

```text
GPS Service
     ↓
Kafka
     ↓
Vehicle Location Consumer
     ↓
Trip State
     ↓
Journey Search
```

Events:

```text
BUS_LOCATION_UPDATED
BUS_DELAYED
TRIP_CANCELLED
ROUTE_CHANGED
STOP_CLOSED
FARE_CHANGED
```

---

# 59. Caching

Bus schedules don't change every second.

Cache:

```text
Route → Stops

Stop → Routes

Route → Trips

Trip → StopTimes
```

Possible:

```text
Redis
```

Example:

```text
route:10A:stops

trip:1001:stoptimes
```

---

# 60. Search Result Caching

Popular searches can be cached.

Example:

```text
Ameerpet → Secunderabad
10:00
```

Cache key:

```text
source:destination:date:time:profile
```

However, real-time systems must invalidate or shorten TTL when live bus conditions change.

---

# 61. Database Scaling

Start with:

```text
PostgreSQL
```

Use indexes on:

```text
stop.id
route.id
route_stop.route_id
route_stop.stop_id
route_stop.sequence
trip.route_id
trip_stop_time.trip_id
trip_stop_time.stop_id
```

For geospatial queries:

```text
PostGIS
```

---

# 62. Read/Write Separation

Operational data:

```text
Route changes
Bus changes
Schedule updates
```

are relatively low volume.

Search traffic can be extremely high.

Therefore eventually:

```text
Primary DB
    ↓
Read Replicas
    ↓
Search Services
```

---

# 63. Search-Oriented Data Model

For very large systems, relational normalization alone may not be enough.

Create derived structures:

```text
Stop → Routes

Route → Stops

Stop Pair → Direct Routes

Stop → Upcoming Trips
```

Example:

```text
A → D

Routes:
10A
20B
50C
```

This reduces expensive joins during search.

---

# 64. Precomputation

A major optimization is to precompute frequently used relationships.

For example:

```text
Stop A
   ↓
Possible destination stops
   ↓
Candidate routes
```

But do not precompute every possible source-destination pair if the network is huge.

Use:

```text
Selective precomputation
+
Graph/index search
```

---

# 65. GeoHash/H3 Optimization

For location-based search:

```text
User GPS
   ↓
H3 / Geohash
   ↓
Nearby cells
   ↓
Candidate stops
```

Then perform exact distance calculation only for candidates.

This is a classic:

```text
Index Filter
      ↓
Exact Calculation
```

pattern.

---

# 66. Search Optimization Pipeline

The search engine should avoid exploring the entire network.

Use:

```text
Geospatial filtering
        ↓
Stop filtering
        ↓
Route filtering
        ↓
Trip filtering
        ↓
Journey generation
        ↓
Constraint pruning
        ↓
Ranking
```

This is much more scalable than:

```text
Load all buses
      ↓
Compare everything
```

---

# 67. Pagination

Do not return:

```text
10,000 journeys
```

Use:

```text
limit
offset
```

or preferably cursor-based pagination.

Example:

```http
GET /journeys?limit=20&cursor=abc
```

---

# 68. Top-K Search

Most users only need:

```text
Top 5
Top 10
Top 20
```

Do not fully sort millions of candidates if only the top 20 are required.

Use:

```text
Top-K selection
```

where appropriate.

---

# 69. Real-Time Bus Tracking

Future enhancement:

```text
Bus GPS
   ↓
Location Service
   ↓
Event Stream
   ↓
Trip Prediction Engine
   ↓
Updated ETA
   ↓
Journey Search
```

Instead of:

```text
Scheduled arrival:

10:30
```

the system could say:

```text
Expected arrival:

10:37
```

---

# 70. Delay-Aware Routing

Suppose:

```text
Bus A

Scheduled:
10:00

Actual:
10:15
```

The routing engine should consider:

```text
Current bus location
Current delay
Predicted arrival
```

This changes the search graph dynamically.

---

# 71. Dynamic ETA

Introduce:

```text
EtaProvider
```

Interface:

```java
interface EtaProvider {

    Duration estimateArrival(
        Trip trip,
        Stop stop
    );

}
```

Implementations:

```text
ScheduledEtaProvider
RealtimeEtaProvider
TrafficAwareEtaProvider
```

This follows the Open/Closed Principle.

---

# 72. Fare Calculation

Do not hardcode fare.

Create:

```java
interface FareCalculator {

    Money calculate(Journey journey);

}
```

Implement:

```text
FlatFareCalculator
ZoneFareCalculator
DistanceFareCalculator
DynamicFareCalculator
```

Future fare rules can be introduced independently.

---

# 73. Operator Integration

Different bus operators may provide data differently.

Create:

```text
OperatorAdapter
```

Example:

```text
Operator A API
        ↓
OperatorAAdapter
        ↓
Internal Transport Model
```

Another:

```text
Operator B CSV
        ↓
OperatorBAdapter
        ↓
Internal Transport Model
```

The core system remains independent of external formats.

---

# 74. Standard Transport Data

For public transport, consider supporting **GTFS-like data structures**.

Conceptually:

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

This makes integration with transport operators easier.

---

# 75. Calendar and Service Days

Trips should not simply have:

```text
trip.startTime
```

because schedules depend on:

```text
Monday
Tuesday
Weekend
Holiday
Special service
```

Create:

```text
ServiceCalendar
```

Example:

```text
Route 10A

Monday-Friday:
Every 15 minutes

Saturday:
Every 30 minutes

Sunday:
Every 45 minutes
```

---

# 76. Exceptions

Support:

```text
Holiday
Strike
Special Event
Road Closure
Temporary Route
```

Example:

```text
Calendar:

Monday-Friday

Exception:

2026-09-20
Service cancelled
```

---

# 77. Stop Closure

A stop can become temporarily unavailable.

Model:

```text
StopStatus
```

such as:

```text
ACTIVE
CLOSED
TEMPORARILY_CLOSED
```

The journey planner should automatically exclude closed stops.

---

# 78. Route Versioning

Never simply overwrite route data when historical correctness matters.

Use:

```text
RouteVersion
```

Example:

```text
Route 10A

Version 1
A → B → C → D

Version 2
A → B → X → D
```

This is useful for:

```text
Historical searches
Audit
Future scheduling
Operator changes
```

---

# 79. Admin System

Create an administration application.

```text
Admin
  ↓
Create Stop
Create Route
Add Route Stops
Create Trip
Configure Schedule
Configure Fare
Configure Ranking
Close Stop
Disable Route
```

---

# 80. Configuration Service

Centralize configurable rules:

```text
Configuration Service
```

Examples:

```text
SEARCH_MAX_RESULTS
SEARCH_MAX_TRANSFERS
MAX_WALKING_DISTANCE
MIN_TRANSFER_TIME

RANKING_PROFILE
RANKING_WEIGHT

CACHE_TTL
```

Configuration can be stored in:

```text
Database
Config Server
Feature Flag System
```

---

# 81. Feature Flags

Future features should be controlled independently.

Example:

```text
REAL_TIME_ETA = true

MULTI_MODAL_SEARCH = false

DYNAMIC_FARE = false

AI_RANKING = false
```

This allows gradual rollout.

---

# 82. Observability

Every search should have:

```text
searchId
```

Track:

```text
Search latency
Candidate routes
Candidate trips
Generated journeys
Filtered journeys
Ranking time
Database latency
Cache hit/miss
```

Example:

```text
SEARCH-123

Total:
240 ms

Stop lookup:
20 ms

Route lookup:
30 ms

Trip lookup:
80 ms

Journey generation:
60 ms

Ranking:
20 ms

Response:
30 ms
```

---

# 83. Distributed Tracing

Use:

```text
OpenTelemetry
```

to trace:

```text
API Gateway
   ↓
Search Service
   ↓
Stop Service
   ↓
Route Service
   ↓
Trip Service
```

This becomes important when the system becomes microservice-based.

---

# 84. Logging

Every request should include:

```text
correlationId
searchId
userId (if authenticated)
```

Avoid logging sensitive personal information.

---

# 85. Fault Tolerance

External services can fail.

For example:

```text
Realtime GPS Service unavailable
```

The system should fall back:

```text
Realtime ETA
      ↓ failure
Scheduled ETA
```

This is another reason for:

```text
EtaProvider
```

abstraction.

---

# 86. Microservices — Don't Start Too Early

A good initial architecture can be a modular monolith:

```text
bus-system
│
├── location
├── route
├── schedule
├── trip
├── journey
├── routing
├── ranking
├── fare
├── configuration
└── realtime
```

All modules can initially run in one Spring Boot application.

Later extract:

```text
Location Service
Route Service
Search Service
Realtime Service
```

when scale justifies it.

---

# 87. Recommended Spring Boot Package Structure

```text
com.example.transport

├── location
│   ├── domain
│   ├── application
│   ├── infrastructure
│   └── api
│
├── route
│   ├── domain
│   ├── application
│   ├── infrastructure
│   └── api
│
├── trip
│   ├── domain
│   ├── application
│   ├── infrastructure
│   └── api
│
├── journey
│   ├── domain
│   ├── application
│   ├── infrastructure
│   └── api
│
├── routing
│
├── ranking
│
├── fare
│
├── configuration
│
└── realtime
```

---

# 88. Domain Layer

Domain should contain business concepts:

```text
Stop
Route
RouteStop
Trip
StopTime
Journey
Leg
Transfer
Fare
```

Avoid putting:

```text
Spring annotations
JPA details
HTTP request objects
```

inside pure domain objects where practical.

---

# 89. Application Layer

Application layer orchestrates use cases.

Example:

```text
SearchJourneyUseCase
```

Flow:

```text
Resolve Location
       ↓
Find Routes
       ↓
Find Trips
       ↓
Build Journeys
       ↓
Filter
       ↓
Rank
```

---

# 90. Infrastructure Layer

Contains:

```text
JPA repositories
PostgreSQL
Redis
Kafka
External APIs
GTFS importer
GPS providers
```

The domain should not depend directly on these technologies.

---

# 91. Testing Strategy

Test at multiple levels.

### Unit Tests

Test:

```text
Route matching
Transfer calculation
Fare calculation
Ranking
Filtering
```

### Integration Tests

Test:

```text
PostgreSQL
Redis
Kafka
```

### End-to-End Tests

Test:

```text
Search API
   ↓
Journey
   ↓
Response
```

---

# 92. Ranking Test Example

Given:

```text
Journey A:
40 minutes
₹30
0 transfers

Journey B:
30 minutes
₹50
1 transfer
```

Test that changing:

```text
FASTEST
```

to:

```text
CHEAPEST
```

changes ranking behavior without modifying journey generation.

This demonstrates the benefit of configuration-driven ranking.

---

# 93. Performance Testing

Test:

```text
100 requests/sec
500 requests/sec
1,000 requests/sec
5,000 requests/sec
```

Measure:

```text
P50
P95
P99
```

response latency.

Also test worst-case:

```text
Major city
Rush hour
Popular source
Popular destination
Many possible routes
```

---

# 94. Security

APIs should support:

```text
Authentication
Authorization
Rate Limiting
Input Validation
API Gateway
HTTPS
```

Admin APIs require stronger authorization.

Example:

```text
USER
OPERATOR
ADMIN
SUPER_ADMIN
```

---

# 95. Rate Limiting

Search APIs can be expensive.

Use:

```text
Rate Limiter
```

Example:

```text
Anonymous:

60 searches/minute

Authenticated:

300 searches/minute
```

These values should be configuration-driven rather than hardcoded.

---

# 96. Future Multimodal Transport

One of the most important architectural enhancements is to avoid making the system "bus-only" internally.

Define:

```text
TransportMode
```

```text
BUS
METRO
TRAIN
WALK
BIKE
TAXI
```

Then:

```text
Journey
   ↓
Leg
   ↓
TransportMode
```

Example:

```text
Walk
 ↓
Metro
 ↓
Walk
 ↓
Bus
 ↓
Walk
```

The same journey model can support all of them.

---

# 97. Multimodal Architecture

```text
                Journey Planner
                      │
       ┌──────────────┼──────────────┐
       ▼              ▼              ▼
     Bus Graph     Metro Graph     Walk Graph
       │              │              │
       └──────────────┼──────────────┘
                      ▼
               Multimodal Router
                      │
                      ▼
                   Journey
```

---

# 98. AI-Based Ranking — Future

Do not start with AI.

First build:

```text
Rule-based ranking
```

Later collect:

```text
Search
Click
Selection
Cancellation
Actual travel
User preferences
```

Then introduce:

```text
ML Ranking Model
```

through:

```text
RankingStrategy
```

Example:

```text
RuleBasedRankingStrategy

MLRankingStrategy
```

The core search engine remains unchanged.

---

# 99. Personalization

Eventually the system can learn:

```text
User usually prefers:

Low fare
Few transfers
AC buses
Morning departure
Low walking
```

The effective ranking becomes:

```text
System Rules
+
User Preferences
+
Real-Time Conditions
```

---

# 100. Event-Based Real-Time Architecture

Final large-scale architecture:

```text
                         ┌──────────────┐
                         │ Web / Mobile │
                         └──────┬───────┘
                                │
                                ▼
                         ┌──────────────┐
                         │ API Gateway  │
                         └──────┬───────┘
                                │
                                ▼
                     ┌──────────────────────┐
                     │ Journey Search       │
                     │ Service              │
                     └──────────┬───────────┘
                                │
              ┌─────────────────┼─────────────────┐
              ▼                 ▼                 ▼
       Location Service    Routing Engine    Ranking Engine
              │                 │                 │
              ▼                 ▼                 ▼
          Geo Index         Graph Index      Configuration
              │                 │                 │
              └─────────────────┼─────────────────┘
                                │
                                ▼
                         Journey Results


             Real-Time World
                    │
                    ▼
              GPS / Operators
                    │
                    ▼
                  Kafka
                    │
          ┌─────────┼──────────┐
          ▼         ▼          ▼
       ETA      Vehicle      Trip
      Engine    State       Updates
          │         │          │
          └─────────┼──────────┘
                    ▼
             Search Engine
```

---

# 101. Recommended Implementation Roadmap

Do not build everything at once.

## Phase 1 — Basic Model

Implement:

```text
Stop
Route
RouteStop
Trip
StopTime
```

Database:

```text
PostgreSQL
```

---

## Phase 2 — Direct Search

Implement:

```text
Source
Destination
Date
Time
```

Search:

```text
Source
 ↓
Routes
 ↓
Destination
 ↓
Trips
```

Return direct buses.

---

## Phase 3 — Ranking

Implement:

```text
TravelTimeCriterion
FareCriterion
TransferCriterion
WalkingDistanceCriterion
```

Create:

```text
RankingEngine
```

---

## Phase 4 — Configuration

Move:

```text
weights
maximum transfers
maximum walking
maximum results
```

to configuration.

---

## Phase 5 — Transfers

Implement:

```text
Transfer
JourneyLeg
MultiLegJourney
```

Then support:

```text
One transfer
```

first.

---

## Phase 6 — Graph Routing

Introduce:

```text
Graph
Dijkstra
Time-dependent routing
```

Eventually evaluate:

```text
RAPTOR
CSA
```

for large public-transit networks.

---

## Phase 7 — Geospatial Search

Introduce:

```text
PostGIS
H3
Geohash
```

for:

```text
"Bus stops near me"
```

---

## Phase 8 — Caching

Introduce:

```text
Redis
```

Cache:

```text
Stops
Routes
Trips
Popular searches
Configuration
```

---

## Phase 9 — Real-Time

Add:

```text
GPS
Kafka
ETA
Delay prediction
Trip cancellation
```

---

## Phase 10 — Multimodal

Introduce:

```text
Bus
Metro
Train
Walk
```

and create:

```text
MultimodalRoutingEngine
```

---

# 102. Important Design Principle

The most important architectural separation is:

```text
                    TRANSPORT DATA
                         │
        ┌────────────────┼────────────────┐
        ▼                ▼                ▼
       Stop            Route             Trip
        │                │                │
        └────────────────┼────────────────┘
                         ▼
                  ROUTING ENGINE
                         │
                         ▼
                    CANDIDATES
                         │
                         ▼
                  CONSTRAINT ENGINE
                         │
                         ▼
                     JOURNEYS
                         │
                         ▼
                   RANKING ENGINE
                         │
                         ▼
                    USER RESULTS
```

Do not mix these responsibilities.

---

# 103. Core Interfaces

A clean design could eventually look like:

```java
public interface StopResolver {

    List<StopCandidate> resolve(Location location);
}
```

```java
public interface RouteMatcher {

    List<RouteCandidate> findRoutes(
        Stop source,
        Stop destination
    );
}
```

```java
public interface TripFinder {

    List<Trip> findTrips(
        Route route,
        SearchCriteria criteria
    );
}
```

```java
public interface JourneyPlanner {

    List<Journey> plan(SearchRequest request);
}
```

```java
public interface JourneyFilter {

    boolean matches(Journey journey);
}
```

```java
public interface RankingCriterion {

    double score(
        Journey journey,
        RankingContext context
    );
}
```

```java
public interface RankingEngine {

    List<Journey> rank(
        List<Journey> journeys,
        RankingConfiguration configuration
    );
}
```

```java
public interface FareCalculator {

    Money calculate(Journey journey);
}
```

```java
public interface EtaProvider {

    Instant estimateArrival(
        Trip trip,
        Stop stop
    );
}
```

This gives you a strong SOLID foundation.

---

# 104. Final Search Flow

The complete flow becomes:

```text
User
 │
 │ Source + Destination + Time
 ▼
API
 │
 ▼
Search Application Service
 │
 ▼
Location Resolver
 │
 ▼
Nearby Stops
 │
 ▼
Route Matcher
 │
 ▼
Candidate Routes
 │
 ▼
Trip Finder
 │
 ▼
Candidate Trips
 │
 ▼
Journey Planner
 │
 ├── Direct Journey
 │
 └── Multi-Leg Journey
 │
 ▼
Constraint Engine
 │
 ├── Max Transfers
 ├── Max Walking
 ├── Max Fare
 ├── Time Window
 └── Other Filters
 │
 ▼
Journey Metrics
 │
 ├── Duration
 ├── Fare
 ├── Waiting
 ├── Walking
 └── Transfers
 │
 ▼
Ranking Engine
 │
 ├── Fastest
 ├── Cheapest
 ├── Fewest Transfers
 ├── Least Walking
 └── Personalized
 │
 ▼
Top-K Journeys
 │
 ▼
API Response
 │
 ▼
Web / Mobile UI
```

---

# 105. Possible Future Enhancements

Once the core system is stable, the architecture can evolve toward:

```text
✓ Live bus tracking
✓ Real-time ETA
✓ Traffic-aware routing
✓ Bus crowd prediction
✓ Seat availability
✓ Online ticket booking
✓ Dynamic pricing
✓ Digital ticket
✓ QR validation
✓ Driver application
✓ Operator dashboard
✓ Route optimization
✓ Automatic schedule generation
✓ Service disruption alerts
✓ Push notifications
✓ Favorite routes
✓ Journey history
✓ Personalized ranking
✓ ML-based ranking
✓ Multimodal transportation
✓ Accessibility-aware routing
✓ Carbon-emission-aware routing
✓ Event-aware route planning
```

---

# 106. Final Architecture Philosophy

The system should follow this principle:

```text
DATA
 ↓
DOMAIN
 ↓
ALGORITHM
 ↓
CONSTRAINTS
 ↓
RANKING
 ↓
PRESENTATION
```

And not:

```text
Controller
   ↓
Huge BusService.java
   ↓
Everything
```

The goal is that adding a new feature such as:

```text
"Prefer AC buses"
```

should require something similar to:

```text
AcPreferenceCriterion
```

rather than changing:

```text
RouteService
TripService
SearchService
Database
Controller
```

Likewise, replacing:

```text
Dijkstra
```

with:

```text
RAPTOR
```

should be possible through:

```text
RoutingAlgorithm
```

without rewriting the API.

Similarly:

```text
PostgreSQL
```

can remain the source of truth while:

```text
Redis
H3
Graph Index
Kafka
```

are introduced as optimized infrastructure around it.

That separation is what makes the system **scalable, configurable, maintainable and extensible**.