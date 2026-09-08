# DYNAMIC SURGE PRICING

## From Global Surge to Real-Time Per-Polygon Surge Pricing
This is Uber Eats pricing brain.

### 💡 IDEA
Each delivery zone (polygon) has:
- demand
- supply
- surge multiplier


### SURGE FORMULA

$$
\text{Surge} = \frac{\text{Demand}}{\text{Supply} + 1}
$$

ZONE MODEL
```java
class DeliveryZone {
    Long id;
    String geoHash;
    int activeOrders;
    int availableDrivers;
}
```

```java
/**
 * Calculates the surge multiplier for a store based on the current
 * demand-to-driver ratio in its delivery zone.
 *
 * A multiplier of 1.0 represents normal pricing. Higher values indicate
 * increased demand relative to available drivers, capped at 3.0x.
 *
 * @param storeId the store used to determine its delivery zone
 * @return surge multiplier between 1.0x and 3.0x
 */
@Service
public class SurgePricingService {
    private final Map<String, DeliveryZone> zones = new ConcurrentHashMap<>();

    public double getSurgeMultiplier(Long storeId) {
        DeliveryZone zone = zones.get(getZoneKey(storeId));
        if (zone == null) return 1.0;

        double surge = (double) zone.getActiveOrders()
                / (zone.getAvailableDrivers() + 1);

        // 1.0 → normal pricing
        // 1.5 → 1.5x pricing
        // 2.0 → 2x pricing
        // 3.0 → 3x pricing
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




---

# Table of Contents

1. [What Are We Building?](#1-what-are-we-building)
2. [The Surge Pricing Problem](#2-the-surge-pricing-problem)
3. [Why Static Pricing Is Not Enough](#3-why-static-pricing-is-not-enough)
4. [What Dynamic Surge Pricing Must Decide](#4-what-dynamic-surge-pricing-must-decide)
5. [Global Surge vs Geographic Surge](#5-global-surge-vs-geographic-surge)
6. [From Radius to Polygon](#6-from-radius-to-polygon)
7. [Why Polygon-Based Surge Is Better](#7-why-polygon-based-surge-is-better)
8. [Complete Surge Pricing Flow](#8-complete-surge-pricing-flow)
9. [Core Concepts](#9-core-concepts)
10. [Demand](#10-demand)
11. [Supply](#11-supply)
12. [Demand-to-Supply Ratio](#12-demand-to-supply-ratio)
13. [Effective Driver Supply](#13-effective-driver-supply)
14. [Unassigned Orders](#14-unassigned-orders)
15. [Driver Availability](#15-driver-availability)
16. [Driver Capacity](#16-driver-capacity)
17. [Geographic Zones](#17-geographic-zones)
18. [Grid vs Polygon](#18-grid-vs-polygon)
19. [Polygon Data Model](#19-polygon-data-model)
20. [Polygon Boundaries](#20-polygon-boundaries)
21. [Point-in-Polygon](#21-point-in-polygon)
22. [Finding the Customer Polygon](#22-finding-the-customer-polygon)
23. [Finding the Driver Polygon](#23-finding-the-driver-polygon)
24. [Demand Aggregation Per Polygon](#24-demand-aggregation-per-polygon)
25. [Supply Aggregation Per Polygon](#25-supply-aggregation-per-polygon)
26. [Polygon Demand/Supply Snapshot](#26-polygon-demandsupply-snapshot)
27. [Basic Surge Multiplier](#27-basic-surge-multiplier)
28. [Surge Tiers](#28-surge-tiers)
29. [Minimum and Maximum Surge](#29-minimum-and-maximum-surge)
30. [Smoothing Surge Changes](#30-smoothing-surge-changes)
31. [Hysteresis](#31-hysteresis)
32. [Time-Based Surge](#32-time-based-surge)
33. [Traffic-Aware Surge](#33-traffic-aware-surge)
34. [ETA-Aware Surge](#34-eta-aware-surge)
35. [Weather-Aware Surge](#35-weather-aware-surge)
36. [Event-Aware Surge](#36-event-aware-surge)
37. [Polygon Pressure Score](#37-polygon-pressure-score)
38. [Dynamic Surge Score](#38-dynamic-surge-score)
39. [Weighted Surge Model](#39-weighted-surge-model)
40. [Surge Price Calculation](#40-surge-price-calculation)
41. [Delivery Fee Calculation](#41-delivery-fee-calculation)
42. [Example Surge Calculation](#42-example-surge-calculation)
43. [Different Surge Types](#43-different-surge-types)
44. [Customer Location vs Pickup Location](#44-customer-location-vs-pickup-location)
45. [Store-to-Customer Polygon Path](#45-store-to-customer-polygon-path)
46. [Multi-Polygon Delivery](#46-multi-polygon-delivery)
47. [Which Polygon Determines Surge?](#47-which-polygon-determines-surge)
48. [Polygon Pricing Policy](#48-polygon-pricing-policy)
49. [Polygon Configuration](#49-polygon-configuration)
50. [Redis Architecture](#50-redis-architecture)
51. [Redis GEO](#51-redis-geo)
52. [Redis Polygon State](#52-redis-polygon-state)
53. [Real-Time Driver Location](#53-real-time-driver-location)
54. [Real-Time Order State](#54-real-time-order-state)
55. [Surge Calculation Engine](#55-surge-calculation-engine)
56. [Java Surge Pricing Service](#56-java-surge-pricing-service)
57. [Polygon Service](#57-polygon-service)
58. [Demand Aggregation Service](#58-demand-aggregation-service)
59. [Supply Aggregation Service](#59-supply-aggregation-service)
60. [Surge Scoring Service](#60-surge-scoring-service)
61. [Pricing Service](#61-pricing-service)
62. [Surge Orchestrator](#62-surge-orchestrator)
63. [Surge Request](#63-surge-request)
64. [Surge Response](#64-surge-response)
65. [Real-Time Surge Updates](#65-real-time-surge-updates)
66. [Kafka Integration](#66-kafka-integration)
67. [WebSocket Integration](#67-websocket-integration)
68. [Surge Heatmap](#68-surge-heatmap)
69. [Polygon Visualization](#69-polygon-visualization)
70. [Customer Price Display](#70-customer-price-display)
71. [Surge Expiration](#71-surge-expiration)
72. [Surge Recalculation](#72-surge-recalculation)
73. [Surge Audit History](#73-surge-audit-history)
74. [Preventing Price Oscillation](#74-preventing-price-oscillation)
75. [Preventing Surge Manipulation](#75-preventing-surge-manipulation)
76. [Fairness and Pricing Limits](#76-fairness-and-pricing-limits)
77. [Failure Handling](#77-failure-handling)
78. [Fallback Pricing](#78-fallback-pricing)
79. [ML-Based Surge Prediction](#79-ml-based-surge-prediction)
80. [ML Feature Engineering](#80-ml-feature-engineering)
81. [Production ML Architecture](#81-production-ml-architecture)
82. [Complete Polygon Surge Flow](#82-complete-polygon-surge-flow)
83. [Complete Order Pricing Flow](#83-complete-order-pricing-flow)
84. [Complete Store + Driver + Surge Flow](#84-complete-store--driver--surge-flow)
85. [Production Architecture](#85-production-architecture)
86. [Implementation Phases](#86-implementation-phases)
87. [Final Architecture](#87-final-architecture)
88. [Final Success Criteria](#88-final-success-criteria)
89. [The Key Architectural Principle](#89-the-key-architectural-principle)

---

# 1. What Are We Building?

We want to dynamically calculate delivery pricing based on:

```text
Demand
+
Driver Supply
+
Geographic Location
+
Time
+
Traffic
+
ETA
+
Weather
+
Events
+
Operational Capacity
```

Instead of having:

```text
Hyderabad
    |
    +---- Surge = 1.2x
```

we want:

```text
Hyderabad
 |
 +---- Polygon A → 1.0x
 |
 +---- Polygon B → 1.4x
 |
 +---- Polygon C → 1.8x
 |
 +---- Polygon D → 1.1x
```

Therefore two customers in the same city can receive different surge pricing.

---

# 2. The Surge Pricing Problem

Suppose:

```text
Area A

Orders = 100
Available Drivers = 80
```

Supply is sufficient.

But:

```text
Area B

Orders = 100
Available Drivers = 20
```

Area B has much higher demand pressure.

Applying one city-wide surge:

```text
Hyderabad → 1.5x
```

would incorrectly charge customers in Area A.

We need:

```text
Area A → normal pricing
Area B → surge pricing
```

---

# 3. Why Static Pricing Is Not Enough

Static pricing might be:

```text
Base delivery fee = ₹30
```

But demand can change rapidly.

Example:

```text
12:00 PM
Orders = 100
Drivers = 100

Surge = 1.0x
```

At:

```text
12:30 PM
Orders = 200
Drivers = 80

Surge = 1.5x
```

At:

```text
1:30 PM
Orders = 70
Drivers = 120

Surge = 1.0x
```

Therefore pricing must react to real-time operational conditions.

---

# 4. What Dynamic Surge Pricing Must Decide

The pricing engine should answer:

```text
Is surge active?

Where is surge active?

How strong is the surge?

Which customers are affected?

What is the final delivery fee?

How long should the surge remain active?

When should the surge be reduced?

What happens if demand suddenly changes?
```

---

# 5. Global Surge vs Geographic Surge

## Global

```text
Entire city
    |
    v
1.5x
```

Simple but inaccurate.

---

## Polygon

```text
City
 |
 +--- Polygon A → 1.0x
 |
 +--- Polygon B → 1.3x
 |
 +--- Polygon C → 1.7x
```

Much more precise.

---

# 6. From Radius to Polygon

An early implementation might use:

```text
Customer
    |
    v
5 km radius
    |
    v
Surge = 1.5x
```

But a radius is circular.

Real demand zones are not circular.

For example:

```text
Airport
IT Park
Residential Area
Shopping Mall
```

have irregular boundaries.

Therefore we eventually move to:

```text
Polygon
```

---

# 7. Why Polygon-Based Surge Is Better

Consider:

```text
Polygon A
Residential Area

Polygon B
Business District

Polygon C
Airport
```

Each area may have completely different:

```text
demand
driver supply
traffic
order density
pricing
```

Polygon-based pricing allows independent control.

---

# 8. Complete Surge Pricing Flow

```text
Order Created
      |
      v
Customer Location
      |
      v
Find Customer Polygon
      |
      v
Read Polygon State
      |
      +---- Demand
      +---- Drivers
      +---- Traffic
      +---- ETA
      +---- Weather
      |
      v
Calculate Pressure
      |
      v
Calculate Surge Multiplier
      |
      v
Apply Pricing Rules
      |
      v
Calculate Delivery Fee
      |
      v
Return Price
```

---

# 9. Core Concepts

The system has four primary concepts:

```text
Demand
Supply
Geography
Price
```

Everything else modifies these four concepts.

---

# 10. Demand

Demand represents the number of delivery requests requiring fulfillment.

Examples:

```text
ordersCreated
ordersPending
ordersWaitingForDriver
ordersBeingPrepared
```

Not every order should necessarily count equally.

---

# 11. Supply

Supply represents available drivers.

But:

```text
Total Drivers
```

is not the same as:

```text
Available Drivers
```

For surge pricing we care about drivers who can actually accept work.

---

# 12. Demand-to-Supply Ratio

A basic model:

```text
pressure =
demand / availableDrivers
```

Example:

```text
Demand = 100
Drivers = 100

Pressure = 1.0
```

Another area:

```text
Demand = 100
Drivers = 50

Pressure = 2.0
```

Higher pressure generally indicates greater need for surge.

---

# 13. Effective Driver Supply

Not every driver should count as fully available.

Suppose:

```text
10 drivers
```

but:

```text
5 are delivering
2 are going offline
1 is far away
2 are available
```

Effective supply may be:

```text
2
```

or perhaps:

```text
2 + partially available drivers
```

depending on the business model.

A better concept is:

```text
effectiveSupply
```

rather than simply:

```text
driverCount
```

---

# 14. Unassigned Orders

One useful demand metric is:

```text
unassignedOrders
```

Example:

```text
Polygon A

Pending orders = 20
Assigned orders = 5
Unassigned orders = 15
```

The 15 unassigned orders represent immediate driver demand.

---

# 15. Driver Availability

A driver may have statuses:

```text
OFFLINE
ONLINE
AVAILABLE
ASSIGNED
PICKING_UP
DELIVERING
```

For surge calculations:

```text
AVAILABLE
```

is the strongest supply signal.

---

# 16. Driver Capacity

A driver currently finishing a delivery may become available soon.

Therefore we can classify:

```text
Immediate Supply
Near-Term Supply
Unavailable Supply
```

Example:

```text
Available now = 10
ETA to availability < 10 min = 5
Busy = 20
```

Effective supply could consider both.

---

# 17. Geographic Zones

A city can be divided into:

```text
Zone A
Zone B
Zone C
Zone D
```

These zones are the fundamental unit for geographic surge.

---

# 18. Grid vs Polygon

## Grid

```text
+----+----+----+
| A  | B  | C  |
+----+----+----+
| D  | E  | F  |
+----+----+----+
```

Easy to calculate.

Excellent for:

```text
high-volume real-time aggregation
```

---

## Polygon

```text
       _______
     /         \
    / Polygon A \
   /             \
   \      ______/
    \____/
```

More accurate for business-defined areas.

---

# 19. Polygon Data Model

A polygon can contain:

```java
class ServicePolygon {

    UUID id;

    String name;

    String geometry;

    PolygonType type;

    boolean active;
}
```

Geometry should ideally be stored in a spatial database using a geometry type rather than a plain string.

---

# 20. Polygon Boundaries

Example conceptual polygon:

```text
Polygon A

[
  [17.3850, 78.4867],
  [17.3900, 78.4950],
  [17.3800, 78.5050],
  [17.3750, 78.4900]
]
```

The polygon defines exactly which geographic region it represents.

---

# 21. Point-in-Polygon

Given:

```text
Customer latitude
Customer longitude
```

we need to determine:

```text
Which polygon contains this point?
```

Conceptually:

```text
point
  |
  v
pointInPolygon()
  |
  +---- Polygon A
  +---- Polygon B
  +---- Polygon C
```

This should be optimized because it may be executed for every order.

---

# 22. Finding the Customer Polygon

The pricing flow begins with:

```text
customerLocation
```

Then:

```text
polygonService.findContainingPolygon(
    latitude,
    longitude
)
```

Result:

```text
polygonId = POLYGON_42
```

Then the engine retrieves:

```text
POLYGON_42
    |
    +---- demand
    +---- supply
    +---- surge
```

---

# 23. Finding the Driver Polygon

Driver locations can also be mapped to polygons.

Every GPS update:

```text
Driver GPS
    |
    v
Find Polygon
    |
    v
Update Polygon Supply
```

Example:

```text
Driver A → Polygon 10
Driver B → Polygon 10
Driver C → Polygon 11
```

Supply:

```text
Polygon 10 → 2 drivers
Polygon 11 → 1 driver
```

---

# 24. Demand Aggregation Per Polygon

When an order is created:

```text
Order
 |
 v
Customer Location
 |
 v
Polygon
 |
 v
Increment Demand
```

Example:

```text
Polygon 10

pendingOrders = 25
```

---

# 25. Supply Aggregation Per Polygon

When a driver becomes available:

```text
Driver
 |
 v
GPS Location
 |
 v
Polygon
 |
 v
Increment Available Supply
```

If the driver moves:

```text
Polygon A → Polygon B
```

then:

```text
Polygon A supply -= 1
Polygon B supply += 1
```

This is a critical real-time operation.

---

# 26. Polygon Demand/Supply Snapshot

Each polygon can maintain:

```json
{
  "polygonId": "P100",
  "pendingOrders": 25,
  "availableDrivers": 10,
  "activeDrivers": 18,
  "averageEtaMinutes": 14,
  "trafficScore": 0.72
}
```

This becomes the input to the surge engine.

---

# 27. Basic Surge Multiplier

A simple initial model:

```text
pressure = demand / supply
```

Then map pressure to multiplier.

Example:

```text
pressure < 1.0
→ 1.0x

1.0 - 1.5
→ 1.1x

1.5 - 2.0
→ 1.3x

2.0 - 3.0
→ 1.5x

> 3.0
→ 2.0x
```

The exact thresholds are business-configurable.

---

# 28. Surge Tiers

A practical configuration:

```text
NORMAL
1.0x

LOW
1.1x

MEDIUM
1.3x

HIGH
1.5x

VERY_HIGH
1.8x
```

Example:

```java
enum SurgeLevel {
    NORMAL,
    LOW,
    MEDIUM,
    HIGH,
    VERY_HIGH
}
```

---

# 29. Minimum and Maximum Surge

Never allow the formula to produce unlimited prices.

For example:

```text
minimumMultiplier = 1.0
maximumMultiplier = 2.0
```

Then:

```text
multiplier =
    clamp(
        calculatedMultiplier,
        minimumMultiplier,
        maximumMultiplier
    );
```

This protects the pricing system from extreme values.

---

# 30. Smoothing Surge Changes

Without smoothing:

```text
1.2x
1.5x
1.1x
1.7x
1.0x
```

could occur within minutes.

This produces a poor customer experience.

Instead:

```text
1.2x
1.3x
1.4x
1.4x
1.3x
```

Use gradual transitions.

---

# 31. Hysteresis

Suppose surge activates at:

```text
pressure >= 1.5
```

Do not immediately deactivate it at:

```text
pressure < 1.5
```

Instead:

```text
Activate:
pressure >= 1.5

Deactivate:
pressure <= 1.3
```

This prevents rapid on/off oscillation.

---

# 32. Time-Based Surge

Demand varies by time.

Example:

```text
Lunch
12:00 → 2:00 PM

Dinner
7:00 → 10:00 PM
```

A polygon may have a higher baseline during these periods.

For example:

```text
baseMultiplier = 1.1
```

during peak hours.

---

# 33. Traffic-Aware Surge

Traffic affects driver productivity.

Example:

```text
Normal traffic
→ 1.0

Heavy traffic
→ 1.2
```

Traffic can therefore contribute to the pressure score.

However, avoid simply multiplying every signal independently because this can produce excessive prices.

Instead normalize signals into a common score.

---

# 34. ETA-Aware Surge

Suppose:

```text
Polygon A

Average pickup ETA = 5 min
```

versus:

```text
Polygon B

Average pickup ETA = 25 min
```

Polygon B has significantly higher operational pressure.

ETA can therefore become a useful surge feature.

---

# 35. Weather-Aware Surge

Weather can affect:

```text
driver availability
travel time
order volume
```

For example:

```text
Heavy rain
```

may cause:

```text
fewer drivers
+
longer travel time
+
higher demand
```

This should increase operational pressure only when the actual data supports it.

---

# 36. Event-Aware Surge

Large events can create localized demand.

Examples:

```text
stadium
concert
festival
shopping mall
airport
```

Instead of increasing prices across the entire city:

```text
Event Polygon
    |
    v
Higher localized pressure
```

This is another major benefit of polygons.

---

# 37. Polygon Pressure Score

Instead of using only:

```text
demand / supply
```

we can create:

```text
pressureScore =
    demandPressure
  + supplyPressure
  + etaPressure
  + trafficPressure
  + timePressure
```

Normalize each component to:

```text
0.0 → 1.0
```

---

# 38. Dynamic Surge Score

Example:

```text
demandScore = 0.80
supplyScore = 0.70
etaScore = 0.60
trafficScore = 0.50
timeScore = 0.30
```

Weighted:

```text
surgeScore =
    0.40 × demandScore
  + 0.30 × supplyScore
  + 0.15 × etaScore
  + 0.10 × trafficScore
  + 0.05 × timeScore
```

---

# 39. Weighted Surge Model

The weights should be configurable:

```java
class SurgeWeights {

    double demandWeight;

    double supplyWeight;

    double etaWeight;

    double trafficWeight;

    double timeWeight;
}
```

This allows the pricing strategy to evolve without rewriting the engine.

---

# 40. Surge Price Calculation

Suppose:

```text
baseDeliveryFee = ₹40
surgeMultiplier = 1.5
```

Then:

```text
surgedFee =
    ₹40 × 1.5

= ₹60
```

Additional pricing components may then be added according to the platform's pricing policy.

---

# 41. Delivery Fee Calculation

A complete delivery price could conceptually be:

```text
deliveryFee =
    baseFee
  + distanceFee
  + timeFee
  + surgeAdjustment
  + applicableOtherCharges
```

For example:

```text
baseFee       = ₹20
distanceFee   = ₹15
surgeFee      = ₹20

total = ₹55
```

The exact commercial formula should remain configurable.

---

# 42. Example Surge Calculation

Suppose Polygon P1 has:

```text
Pending Orders = 40
Available Drivers = 20

pressure = 2.0
```

Assume:

```text
baseMultiplier = 1.0
pressure adjustment = 0.5
```

Then:

```text
surgeMultiplier = 1.5x
```

For:

```text
base delivery fee = ₹40
```

the surge-adjusted component becomes:

```text
₹40 × 1.5 = ₹60
```

---

# 43. Different Surge Types

The architecture can support:

```text
DEMAND_SURGE
WEATHER_SURGE
EVENT_SURGE
CAPACITY_SURGE
TRAFFIC_SURGE
```

But these should feed into a common pricing engine rather than each independently multiplying the final price.

Prefer:

```text
Signals
   |
   v
Pressure Score
   |
   v
Surge Multiplier
```

rather than:

```text
1.2 × 1.3 × 1.4 × 1.2
```

which can quickly become unreasonable.

---

# 44. Customer Location vs Pickup Location

For food delivery there are at least two important locations:

```text
Store
Customer
```

The customer polygon may indicate:

```text
delivery demand
```

while the store polygon may indicate:

```text
pickup supply conditions
```

Therefore both can matter.

---

# 45. Store-to-Customer Polygon Path

Example:

```text
Store
  |
  v
Polygon A
  |
  v
Polygon B
  |
  v
Polygon C
  |
  v
Customer
```

Each polygon may have different:

```text
traffic
demand
driver availability
```

This can be useful for advanced pricing and ETA.

---

# 46. Multi-Polygon Delivery

Suppose:

```text
Store = Polygon A
Customer = Polygon C
```

Route:

```text
A → B → C
```

The engine can calculate:

```text
routePressure =
pressure(A)
+
pressure(B)
+
pressure(C)
```

However, do not simply sum all polygon surge multipliers.

The pricing engine should determine how much each segment contributes.

---

# 47. Which Polygon Determines Surge?

For a first implementation:

```text
Customer Polygon
```

can determine the surge.

This is simple and predictable.

Later:

```text
Customer Polygon
+
Store Polygon
+
Route Polygons
```

can contribute to pricing.

Recommended evolution:

```text
Phase 1
Customer Polygon

Phase 2
Customer + Store Polygon

Phase 3
Route Polygon Analysis
```

---

# 48. Polygon Pricing Policy

Each polygon can have configuration:

```java
class PolygonPricingPolicy {

    UUID polygonId;

    BigDecimal minimumFee;

    BigDecimal maximumFee;

    double maximumSurgeMultiplier;

    boolean surgeEnabled;

    double activationThreshold;

    double deactivationThreshold;
}
```

---

# 49. Polygon Configuration

Example:

```text
Polygon A

surgeEnabled = true
maxMultiplier = 1.5
activationThreshold = 1.5
deactivationThreshold = 1.3
```

Another polygon:

```text
Polygon B

surgeEnabled = true
maxMultiplier = 2.0
activationThreshold = 1.8
deactivationThreshold = 1.4
```

This allows different operational policies.

---

# 50. Redis Architecture

Redis is useful for high-frequency operational state.

Conceptually:

```text
Redis
 |
 +---- Driver Locations
 |
 +---- Polygon Demand
 |
 +---- Polygon Supply
 |
 +---- Polygon Pressure
 |
 +---- Current Surge
 |
 +---- Short-lived Pricing Cache
```

---

# 51. Redis GEO

Driver locations can be stored using:

```text
GEOADD drivers
```

This allows:

```text
nearby drivers
```

to be found efficiently.

The surge engine can use this information to understand local supply.

---

# 52. Redis Polygon State

A polygon state might be represented conceptually as:

```text
surge:polygon:P100

pendingOrders = 25
availableDrivers = 10
pressureScore = 0.78
surgeMultiplier = 1.5
updatedAt = ...
```

This is operational state.

Persistent pricing history belongs elsewhere.

---

# 53. Real-Time Driver Location

Driver GPS flow:

```text
Driver
 |
 | GPS update
 v
Driver Tracking Service
 |
 +---- Redis GEO
 |
 +---- Polygon Mapping
 |
 v
Polygon Supply State
```

When a driver moves between polygons:

```text
P1 → P2
```

update:

```text
P1 supply -= 1
P2 supply += 1
```

---

# 54. Real-Time Order State

Order flow:

```text
Order Created
 |
 v
Customer Polygon
 |
 v
Polygon Demand += 1
```

When assigned:

```text
Polygon Demand -= 1
```

depending on the chosen demand definition.

The important part is to define exactly what states count as demand and keep that definition consistent.

---

# 55. Surge Calculation Engine

Create a dedicated component:

```java
@Service
public class SurgeCalculationEngine {

    public SurgeResult calculate(
        PolygonState state
    ) {
        // calculate pressure
        // calculate surge score
        // calculate multiplier
        // apply limits
        // return result
    }
}
```

---

# 56. Java Surge Pricing Service

Expose a pricing abstraction:

```java
public interface SurgePricingService {

    SurgeResult calculate(
        SurgeRequest request
    );
}
```

Implementation:

```java
@Service
public class DynamicSurgePricingService
        implements SurgePricingService {

    // implementation
}
```

This keeps the pricing logic replaceable.

---

# 57. Polygon Service

Responsible for:

```text
point → polygon
```

Example:

```java
public interface PolygonService {

    Optional<ServicePolygon> findContainingPolygon(
        double latitude,
        double longitude
    );
}
```

---

# 58. Demand Aggregation Service

Responsible for:

```text
orders → polygon demand
```

Example:

```java
public interface DemandAggregationService {

    PolygonDemand getDemand(
        UUID polygonId
    );
}
```

---

# 59. Supply Aggregation Service

Responsible for:

```text
drivers → polygon supply
```

Example:

```java
public interface SupplyAggregationService {

    PolygonSupply getSupply(
        UUID polygonId
    );
}
```

---

# 60. Surge Scoring Service

Responsible for:

```text
signals → normalized score
```

Example:

```java
public interface SurgeScoringService {

    double calculateScore(
        SurgeFeatures features
    );
}
```

---

# 61. Pricing Service

Responsible for:

```text
base price
+
surge multiplier
=
final price
```

Keep this separate from geographic calculations.

---

# 62. Surge Orchestrator

The orchestrator:

```text
SurgePricingService
       |
       +-- PolygonService
       |
       +-- DemandService
       |
       +-- SupplyService
       |
       +-- ETAService
       |
       +-- TrafficService
       |
       +-- SurgeScoringService
       |
       +-- PricingService
```

This keeps responsibilities separated.

---

# 63. Surge Request

Example:

```java
public record SurgeRequest(
    double latitude,
    double longitude,
    UUID storeId,
    BigDecimal baseDeliveryFee
) {}
```

---

# 64. Surge Response

Example:

```java
public record SurgeResult(
    UUID polygonId,
    double pressureScore,
    double surgeMultiplier,
    BigDecimal surgeAmount,
    BigDecimal finalDeliveryFee,
    Instant expiresAt
) {}
```

---

# 65. Real-Time Surge Updates

Surge should not necessarily be recalculated only when a customer opens checkout.

It can be continuously recalculated.

Example:

```text
Every 30 seconds
        |
        v
Calculate polygon state
        |
        v
Update surge
```

Or use event-driven recalculation:

```text
Order Created
Driver Available
Driver Moved
Order Assigned
```

---

# 66. Kafka Integration

Useful events:

```text
ORDER_CREATED
ORDER_ASSIGNED
ORDER_CANCELLED
DRIVER_ONLINE
DRIVER_OFFLINE
DRIVER_LOCATION_UPDATED
DRIVER_AVAILABLE
DRIVER_ASSIGNED
STORE_BUSY
STORE_AVAILABLE
```

These events can trigger polygon state updates.

---

# 67. WebSocket Integration

The dashboard can subscribe to:

```text
/topic/surge
```

Example:

```json
{
  "polygonId": "P100",
  "surgeMultiplier": 1.5,
  "pressureScore": 0.81,
  "updatedAt": "..."
}
```

The dashboard immediately updates the polygon visualization.

---

# 68. Surge Heatmap

The frontend can render:

```text
Polygon A → Normal
Polygon B → Medium
Polygon C → High
Polygon D → Very High
```

Conceptually:

```text
+--------+--------+
|        | HIGH   |
| NORMAL |        |
+--------+--------+
| MEDIUM | VERY   |
|        | HIGH   |
+--------+--------+
```

This gives operations teams a live view of supply-demand pressure.

---

# 69. Polygon Visualization

Using Leaflet or another mapping library:

```text
Polygon
   |
   v
GeoJSON
   |
   v
Map Layer
```

Each polygon can display:

```text
name
surge level
demand
available drivers
pressure
```

---

# 70. Customer Price Display

The customer should see:

```text
Delivery Fee       ₹60

Base Fee           ₹40
Surge Adjustment   ₹20
```

or another transparent pricing representation appropriate to the product.

Avoid exposing internal scoring details unnecessarily.

---

# 71. Surge Expiration

A surge result should not necessarily remain valid forever.

Example:

```text
surgeMultiplier = 1.5x
expiresAt = 12:35 PM
```

At expiration:

```text
recalculate
```

This prevents stale pricing.

---

# 72. Surge Recalculation

Recalculate when:

```text
Demand changes significantly
Supply changes significantly
Driver movement changes zone supply
Traffic changes
Weather changes
Peak period starts
Peak period ends
```

A hybrid model is useful:

```text
Event-driven updates
+
Periodic reconciliation
```

---

# 73. Surge Audit History

Every surge change should ideally be traceable.

Example:

```text
Polygon P100

12:00 → 1.0x
12:05 → 1.2x
12:10 → 1.4x
12:15 → 1.5x
12:25 → 1.3x
```

Store historical records such as:

```text
polygonId
oldMultiplier
newMultiplier
pressureScore
demand
supply
reason
timestamp
```

This is extremely useful for debugging and analytics.

---

# 74. Preventing Price Oscillation

Bad implementation:

```text
Demand = 20
Supply = 15
→ 1.5x

Demand = 19
Supply = 15
→ 1.0x

Demand = 20
Supply = 15
→ 1.5x
```

This creates unstable pricing.

Use:

```text
smoothing
+
hysteresis
+
minimum duration
```

---

# 75. Preventing Surge Manipulation

A surge system must not blindly react to noisy or malicious signals.

Examples:

```text
fake orders
GPS spoofing
driver status manipulation
artificial demand spikes
```

Use:

```text
validated events
rate limits
anomaly detection
minimum sample sizes
```

before allowing extreme pricing changes.

---

# 76. Fairness and Pricing Limits

The pricing engine should have strict boundaries.

For example:

```text
minimumMultiplier = 1.0x
maximumMultiplier = 2.0x
```

and:

```text
maximum absolute delivery fee
```

should also be configurable.

The system should distinguish between:

```text
Operational optimization
```

and:

```text
unbounded price increases
```

The latter should never be an accidental consequence of the algorithm.

---

# 77. Failure Handling

Possible failures:

```text
Polygon unavailable
Redis unavailable
Driver count unavailable
Traffic service unavailable
Weather service unavailable
Kafka delay
```

The pricing engine should degrade gracefully.

Example:

```text
Traffic unavailable
        |
        v
Ignore traffic feature
        |
        v
Use demand + supply + ETA
```

---

# 78. Fallback Pricing

If the surge engine is unavailable:

```text
base pricing
```

should continue.

For example:

```text
surgeMultiplier = 1.0x
```

rather than:

```text
checkout unavailable
```

unless the business explicitly requires surge calculation.

---

# 79. ML-Based Surge Prediction

Once historical data is available, the system can predict future pressure.

Instead of reacting only to:

```text
current demand
```

the system can predict:

```text
expected demand in 15 minutes
```

Example:

```text
Current:
Demand = 50
Supply = 40

Predicted 15 min:
Demand = 90
Supply = 35
```

The system can gradually adjust before the shortage becomes severe.

---

# 80. ML Feature Engineering

Potential features:

### Demand

```text
ordersPerMinute
ordersLast5Minutes
ordersLast15Minutes
unassignedOrders
```

### Supply

```text
availableDrivers
driverArrivalRate
driverDepartureRate
```

### Geography

```text
polygonId
neighboringPolygonPressure
```

### Time

```text
hour
dayOfWeek
holiday
```

### Traffic

```text
trafficScore
averageRoadSpeed
```

### Historical

```text
historicalDemand
historicalSupply
historicalSurge
```

### External

```text
weather
events
```

---

# 81. Production ML Architecture

A possible architecture:

```text
Real-Time Events
      |
      v
Kafka
      |
      v
Feature Pipeline
      |
      v
Feature Store
      |
      v
ML Model
      |
      v
Demand Prediction
      |
      v
Surge Engine
      |
      v
Polygon Pricing
```

The ML model predicts operational pressure; the pricing engine should still enforce hard pricing limits.

---

# 82. Complete Polygon Surge Flow

```text
Driver GPS
    |
    v
Driver Polygon
    |
    v
Supply +1
```

and:

```text
Order Created
    |
    v
Customer Polygon
    |
    v
Demand +1
```

Then:

```text
Polygon State
    |
    +---- Demand
    +---- Supply
    +---- ETA
    +---- Traffic
    +---- Time
    |
    v
Pressure Score
    |
    v
Surge Multiplier
    |
    v
Polygon Pricing State
```

---

# 83. Complete Order Pricing Flow

```text
Customer
    |
    v
Create Order
    |
    v
Customer Coordinates
    |
    v
Find Polygon
    |
    v
Read Current Surge
    |
    v
Calculate Delivery Fee
    |
    v
Return Price
```

Example:

```text
Base Fee = ₹40

Polygon P100
Surge = 1.5x

Final = ₹60
```

---

# 84. Complete Store + Driver + Surge Flow

This connects the three engines.

```text
                    ORDER
                      |
          +-----------+-----------+
          |                       |
          v                       v
   MULTI-STORE MATCHING      CUSTOMER LOCATION
          |                       |
          v                       v
       STORE                 SURGE POLYGON
          |                       |
          v                       v
   DRIVER MATCHING          DEMAND/SUPPLY
          |                       |
          +-----------+-----------+
                      |
                      v
                 DELIVERY
                      |
                      v
              REAL-TIME TRACKING
```

Now the platform has:

```text
Store Matching
Driver Matching
Surge Pricing
ETA
Route
Real-Time Tracking
```

working together.

---

# 85. Production Architecture

The complete system can look like:

```text
                         CUSTOMER
                            |
                            v
                         ORDER
                            |
                            v
                    ORDER SERVICE
                            |
                            v
                           KAFKA
                            |
          +-----------------+------------------+
          |                 |                  |
          v                 v                  v
   STORE MATCHING     SURGE ENGINE       ANALYTICS
          |                 |
          v                 v
      STORES          POLYGON SERVICE
                            |
             +--------------+--------------+
             |              |              |
             v              v              v
          DEMAND         SUPPLY          ETA
             |              |              |
             +--------------+--------------+
                            |
                            v
                    PRESSURE SCORE
                            |
                            v
                    SURGE MULTIPLIER
                            |
                            v
                    PRICING SERVICE
                            |
                            v
                     CUSTOMER PRICE
```

---

# 86. Implementation Phases

## Phase 1 — Static Surge

Implement:

```text
baseFee
surgeMultiplier
```

manually configured.

---

## Phase 2 — Demand/Supply Surge

Add:

```text
demand
availableDrivers
pressure = demand / supply
```

---

## Phase 3 — Grid-Based Surge

Divide the city into:

```text
geographic cells
```

Calculate:

```text
demand/supply per cell
```

---

## Phase 4 — Polygon Support

Introduce:

```text
ServicePolygon
```

and:

```text
point → polygon
```

mapping.

---

## Phase 5 — Per-Polygon Surge

Every polygon gets:

```text
demand
supply
pressure
surgeMultiplier
```

independently.

---

## Phase 6 — Real-Time Polygon State

Connect:

```text
Driver GPS
Order Events
Kafka
Redis
```

so polygon state changes in real time.

---

## Phase 7 — Advanced Signals

Add:

```text
ETA
Traffic
Time
Store capacity
```

---

## Phase 8 — Route-Aware Pricing

Consider:

```text
Store Polygon
Customer Polygon
Route Polygons
```

---

## Phase 9 — WebSocket Dashboard

Display:

```text
live polygon surge
demand
supply
pressure
```

on the operations dashboard.

---

## Phase 10 — ML Prediction

Predict:

```text
future demand
future supply
future pressure
```

and proactively adjust surge.

---

# 87. Final Architecture

```text
                         REAL-TIME EVENTS
                                |
          +---------------------+---------------------+
          |                     |                     |
          v                     v                     v
       ORDERS                DRIVERS              TRAFFIC
          |                     |                     |
          v                     v                     v
       DEMAND                SUPPLY                ETA
          |                     |                     |
          +---------------------+---------------------+
                                |
                                v
                       POLYGON MAPPING
                                |
                                v
                     POLYGON STATE ENGINE
                                |
             +------------------+------------------+
             |                  |                  |
             v                  v                  v
          DEMAND             SUPPLY              ETA
             |                  |                  |
             +------------------+------------------+
                                |
                                v
                       PRESSURE CALCULATOR
                                |
                                v
                        SURGE SCORING
                                |
                                v
                     SURGE MULTIPLIER
                                |
                                v
                       PRICING ENGINE
                                |
                                v
                        CUSTOMER PRICE
```

For the advanced version:

```text
                 PREDICTION / ML
                       |
                       v
              Future Demand/Supply
                       |
                       v
                 Surge Engine
```

---

# 88. Final Success Criteria

The Dynamic Surge Pricing system is successful when it can:

```text
✓ Calculate demand

✓ Calculate effective driver supply

✓ Calculate demand/supply pressure

✓ Divide the service area geographically

✓ Support grid-based zones

✓ Support arbitrary polygons

✓ Map customers to polygons

✓ Map drivers to polygons

✓ Maintain real-time polygon state

✓ Calculate surge independently per polygon

✓ Apply configurable surge thresholds

✓ Apply minimum/maximum limits

✓ Prevent price oscillation

✓ Support time-based pricing signals

✓ Support ETA-based signals

✓ Support traffic signals

✓ Support weather/event signals

✓ Support route-aware pricing

✓ Recalculate surge dynamically

✓ Publish real-time surge changes

✓ Display polygon surge on the dashboard

✓ Maintain surge audit history

✓ Gracefully fall back to normal pricing

✓ Support future ML-based demand prediction
```

---

# 89. The Key Architectural Principle

The most important evolution is:

```text
DO NOT BUILD:

calculateGlobalSurge()
```

Then:

```text
calculateCitySurge()
```

Instead build:

```text
Location
   |
   v
Find Polygon
   |
   v
Read Polygon State
   |
   +---- Demand
   +---- Supply
   +---- ETA
   +---- Traffic
   +---- Time
   |
   v
Calculate Pressure
   |
   v
Calculate Surge Score
   |
   v
Apply Pricing Policy
   |
   v
Calculate Final Fee
```

The architecture should evolve through:

```text
STATIC PRICING
      |
      v
GLOBAL SURGE
      |
      v
GRID SURGE
      |
      v
POLYGON SURGE
      |
      v
REAL-TIME POLYGON SURGE
      |
      v
MULTI-SIGNAL POLYGON SURGE
      |
      v
PREDICTIVE POLYGON SURGE
```

The final conceptual model is:

```text
                 CITY
                  |
        +---------+---------+
        |         |         |
        v         v         v
     Polygon A Polygon B Polygon C
        |         |         |
        v         v         v
     Demand    Demand    Demand
     Supply    Supply    Supply
     ETA       ETA       ETA
     Traffic   Traffic   Traffic
        |         |         |
        v         v         v
     1.0x      1.5x      1.8x
```

So the pricing engine does **not** ask:

```text
"Is Hyderabad currently in surge?"
```

It asks:

```text
"Given this customer's location, which polygon are they in,
what is the current operational pressure in that polygon,
and what pricing policy should be applied?"
```

And eventually:

```text
"Given the customer's polygon, store polygon, delivery route,
current and predicted demand/supply, ETA and operational conditions,
what is the optimal and bounded delivery price?"
```

That is the foundation for a production-grade **real-time, per-polygon Dynamic Surge Pricing Engine**.