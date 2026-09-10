# MULTI-STORE MATCHING ENGINE

```
Score = distance + price factor + rating + delivery time + surge penalty
```

$$
\text{Score} = w_1D + w_2P + w_3R + w_4T + w_5S
$$

```java
@Service
public class StoreMatchingService {
    @Autowired private StoreRepository storeRepo;
    @Autowired private SurgeService surgeService;

    public List<Store> findBestStores(double lat, double lon) {
        List<Store> stores = storeRepo.findNearbyCandidates(lat, lon);
        List<ScoredStore> scored = new ArrayList<>();

        for (Store s : stores) {
            double distance = haversine(lat, lon, s.getLat(), s.getLon());
            double surgePrice = surgeService.getSurgePrice(s.getId());

            // Lower scores indicate better stores:
            // distance (50%), rating preference (20%), delivery time (20%),
            // and surge pricing (10%) are combined into a single ranking score.
            // Surge price means an extra price applied during high-demand periods
            // For example, imagine a food-delivery system:
            //  - Normal delivery fee: ₹40
            //  - High demand: ₹60
            //  - Very high demand: ₹80
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
is effectively saying:
> Prefer stores that are closer, have better ratings, deliver faster, and have less surge pricing.

**One important note:** `(1 / s.getRating()) * 0.2` means a higher rating produces a lower score, which is consistent with the comment if lower scores are better. However, the units of distance, deliveryTime, and surge are very different, so in a real scoring model you'd usually **normalize these values before applying the weights**.

---

# Table of Contents

1. [What Are We Building?](#1-what-are-we-building)
2. [The Multi-Store Problem](#2-the-multi-store-problem)
3. [Why Nearest Store Is Not Enough](#3-why-nearest-store-is-not-enough)
4. [What the Matching Engine Must Decide](#4-what-the-matching-engine-must-decide)
5. [Complete Matching Flow](#5-complete-matching-flow)
6. [Core Concepts](#6-core-concepts)
7. [Store Data Model](#7-store-data-model)
8. [Store Availability](#8-store-availability)
9. [Store Operating Hours](#9-store-operating-hours)
10. [Store Inventory](#10-store-inventory)
11. [Store Preparation Time](#11-store-preparation-time)
12. [Store Geographic Location](#12-store-geographic-location)
13. [Redis GEO for Store Discovery](#13-redis-geo-for-store-discovery)
14. [Store Candidate Generation](#14-store-candidate-generation)
15. [Candidate Search Radius](#15-candidate-search-radius)
16. [Expanding the Search Radius](#16-expanding-the-search-radius)
17. [Store Eligibility Filtering](#17-store-eligibility-filtering)
18. [Hard Constraints vs Soft Constraints](#18-hard-constraints-vs-soft-constraints)
19. [Delivery Zone Validation](#19-delivery-zone-validation)
20. [Customer Distance](#20-customer-distance)
21. [Driver Availability](#21-driver-availability)
22. [Store Capacity](#22-store-capacity)
23. [Inventory Validation](#23-inventory-validation)
24. [Store Preparation Time](#24-store-preparation-time)
25. [Candidate Feature Vector](#25-candidate-feature-vector)
26. [Store Scoring](#26-store-scoring)
27. [Weighted Scoring Model](#27-weighted-scoring-model)
28. [ETA-Based Store Ranking](#28-eta-based-store-ranking)
29. [Delivery Cost](#29-delivery-cost)
30. [Customer Experience Score](#30-customer-experience-score)
31. [Store Reliability Score](#31-store-reliability-score)
32. [Dynamic Store Ranking](#32-dynamic-store-ranking)
33. [The Best Store Is Not Always the Nearest Store](#33-the-best-store-is-not-always-the-nearest-store)
34. [Multi-Store Order Matching](#34-multi-store-order-matching)
35. [Single-Store vs Multi-Store Order](#35-single-store-vs-multi-store-order)
36. [Cart Validation](#36-cart-validation)
37. [Store Combination Generation](#37-store-combination-generation)
38. [Combination Explosion Problem](#38-combination-explosion-problem)
39. [Optimized Combination Search](#39-optimized-combination-search)
40. [Fulfillment Strategy](#40-fulfillment-strategy)
41. [Order Splitting](#41-order-splitting)
42. [Driver Assignment Impact](#42-driver-assignment-impact)
43. [Store + Driver Joint Optimization](#43-store-driver-joint-optimization)
44. [Production Matching Pipeline](#44-production-matching-pipeline)
45. [Java Matching Engine](#45-java-matching-engine)
46. [Store Candidate Service](#46-store-candidate-service)
47. [Store Eligibility Service](#47-store-eligibility-service)
48. [Store Scoring Service](#48-store-scoring-service)
49. [Matching Engine Orchestrator](#49-matching-engine-orchestrator)
50. [Matching Request](#50-matching-request)
51. [Matching Response](#51-matching-response)
52. [Example Matching Algorithm](#52-example-matching-algorithm)
53. [Example Java Implementation](#53-example-java-implementation)
54. [Real-Time Store State](#54-real-time-store-state)
55. [Store Events](#55-store-events)
56. [WebSocket Store Updates](#56-websocket-store-updates)
57. [Kafka Integration](#57-kafka-integration)
58. [Caching Strategy](#58-caching-strategy)
59. [Database Responsibilities](#59-database-responsibilities)
60. [Redis Responsibilities](#60-redis-responsibilities)
61. [Matching Failure Handling](#61-matching-failure-handling)
62. [Fallback Strategy](#62-fallback-strategy)
63. [Re-Matching](#63-re-matching)
64. [Dynamic Re-Ranking](#64-dynamic-re-ranking)
65. [Machine Learning Enhancement](#65-machine-learning-enhancement)
66. [ML Feature Engineering](#66-ml-feature-engineering)
67. [ML-Based Store Ranking](#67-ml-based-store-ranking)
68. [Production ML Architecture](#68-production-ml-architecture)
69. [Complete Order Flow](#69-complete-order-flow)
70. [Complete Multi-Store Flow](#70-complete-multi-store-flow)
71. [Complete Store + Driver Flow](#71-complete-store-driver-flow)
72. [Production Considerations](#72-production-considerations)
73. [Implementation Phases](#73-implementation-phases)
74. [Final Architecture](#74-final-architecture)
75. [Final Success Criteria](#75-final-success-criteria)
76. [The Key Architectural Principle](#76-the-key-architectural-principle)

# 1. What Are We Building?

A **Multi-Store Matching Engine** decides:

> Given a customer's order, which store or combination of stores should fulfill the order?

For example:

```text
Customer wants:

2 × Chicken Biryani
1 × Coke
1 × Gulab Jamun
```

There may be multiple stores:

```text
Store A
Distance: 1.2 km
Biryani: YES
Coke: YES
Gulab Jamun: NO

Store B
Distance: 2.0 km
Biryani: YES
Coke: YES
Gulab Jamun: YES

Store C
Distance: 0.8 km
Biryani: NO
Coke: YES
Gulab Jamun: YES
```

The engine must determine whether:

```text
Store B
```

is better than:

```text
Store A + Store C
```

It must consider much more than geographic distance.

---

# 2. The Multi-Store Problem

A food-delivery platform may have:

```text
Customer
   |
   +---- Store A
   +---- Store B
   +---- Store C
   +---- Store D
   +---- Store E
```

Each store can have different:

- distance
- inventory
- preparation time
- operating hours
- delivery zone
- capacity
- reliability
- pricing
- rating
- traffic conditions
- driver availability

Therefore:

```text
Nearest Store
```

does not necessarily mean:

```text
Best Store
```

---

# 3. Why Nearest Store Is Not Enough

Consider:

```text
Store A
Distance = 1 km
Preparation Time = 35 min
Traffic = Heavy
Inventory = Partial
```

and:

```text
Store B
Distance = 2.5 km
Preparation Time = 10 min
Traffic = Low
Inventory = Complete
```

Simple distance matching selects:

```text
Store A
```

But actual delivery time might be:

```text
Store A
1 km travel
+ 35 min preparation
+ traffic
= 50 min
```

while:

```text
Store B
2.5 km travel
+ 10 min preparation
+ low traffic
= 25 min
```

Therefore:

```text
Best Store ≠ Nearest Store
```

The matching engine should optimize the **complete fulfillment experience**.

---

# 4. What the Matching Engine Must Decide

The engine may need to answer:

```text
Which store?

Can the store fulfill the entire cart?

How long will preparation take?

How long will delivery take?

Can the store deliver to the customer?

Is the store accepting orders?

Does the store have enough inventory?

Does the store have capacity?

Are drivers available?

What will the customer pay?

What is the expected delivery time?

Is splitting the order better?
```

The engine therefore becomes a decision system.

---

# 5. Complete Matching Flow

The basic flow is:

```text
Order Created
      |
      v
Read Customer Location
      |
      v
Read Cart Items
      |
      v
Find Nearby Stores
      |
      v
Generate Store Candidates
      |
      v
Filter Ineligible Stores
      |
      v
Validate Inventory
      |
      v
Calculate Preparation Time
      |
      v
Calculate Delivery ETA
      |
      v
Calculate Cost
      |
      v
Calculate Store Score
      |
      v
Rank Stores
      |
      v
Select Best Store
      |
      v
Check Multi-Store Possibility
      |
      v
Create Fulfillment Plan
```

---

# 6. Core Concepts

The engine can be divided into five major stages:

```text
1. Candidate Generation

2. Eligibility Filtering

3. Feature Calculation

4. Candidate Scoring

5. Final Selection
```

This separation is extremely important.

Do not put everything into one giant method.

---

# 7. Store Data Model

A simplified store could contain:

```java
class Store {

    UUID id;

    String name;

    double latitude;

    double longitude;

    StoreStatus status;

    boolean acceptingOrders;

    int preparationTimeMinutes;

    int capacity;

    double rating;
}
```

Possible status:

```text
ACTIVE
INACTIVE
BUSY
TEMPORARILY_CLOSED
CLOSED
```

---

# 8. Store Availability

A store should not be considered simply because it exists.

The engine should verify:

```text
Store exists
+
Store is active
+
Store accepts orders
+
Store is currently open
```

For example:

```java
if (!store.isAcceptingOrders()) {
    reject(store);
}
```

---

# 9. Store Operating Hours

A store may operate:

```text
11:00 AM → 11:00 PM
```

If an order arrives at:

```text
11:30 PM
```

the store should not be selected.

The matching engine should therefore consider:

```text
currentTime
storeOpeningTime
storeClosingTime
```

---

# 10. Store Inventory

Inventory is one of the most important constraints.

Suppose the customer requests:

```text
Chicken Biryani × 2
```

Store A has:

```text
quantity = 1
```

Store B has:

```text
quantity = 5
```

Store A cannot fully satisfy the order.

Therefore:

```text
Inventory Validation
```

must happen before final ranking.

---

# 11. Store Preparation Time

Every store may have different preparation times.

For example:

```text
Store A → 15 min
Store B → 35 min
Store C → 10 min
```

Preparation time directly affects:

```text
Customer ETA
```

So the matching engine should not ignore it.

---

# 12. Store Geographic Location

Every store should maintain:

```text
latitude
longitude
```

Example:

```text
Store A
17.3850
78.4867
```

The customer also has:

```text
customerLatitude
customerLongitude
```

These coordinates are used for:

- nearby store discovery
- distance calculation
- delivery ETA
- delivery-zone validation

---

# 13. Redis GEO for Store Discovery

Redis GEO can efficiently find nearby stores.

For example:

```text
GEOADD stores 78.4867 17.3850 store-A
GEOADD stores 78.4900 17.3900 store-B
GEOADD stores 78.4800 17.3800 store-C
```

Then:

```text
GEOSEARCH
```

can retrieve nearby stores.

Conceptually:

```text
Customer
   |
   |  radius search
   v
Redis GEO
   |
   +---- Store A
   +---- Store B
   +---- Store C
   +---- Store D
```

Redis is responsible for **fast geographic candidate discovery**.

---

# 14. Store Candidate Generation

The first stage should intentionally be broad.

Example:

```text
Customer location

Search radius = 5 km

Candidates:
A
B
C
D
E
```

Do not immediately calculate complex scores for every store in the database.

Instead:

```text
Database
   |
   v
Redis GEO
   |
   v
Small Candidate Set
```

This is the first major optimization.

---

# 15. Candidate Search Radius

A configurable radius can be used:

```java
initialRadius = 3 km;
```

If insufficient candidates exist:

```text
3 km
 ↓
5 km
 ↓
8 km
 ↓
10 km
```

For example:

```text
Search 3 km
→ 1 store

Search 5 km
→ 4 stores

Stop
```

There is no reason to search 20 km if enough eligible candidates are already available.

---

# 16. Expanding the Search Radius

Use progressive expansion:

```text
radius = 3 km

while candidates < minimumCandidates:

    radius = radius * 2

    search(radius)
```

Example:

```text
3 km
6 km
12 km
```

The exact strategy should be configurable.

---

# 17. Store Eligibility Filtering

After candidate generation:

```text
Candidate Stores
      |
      v
Eligibility Filter
```

Remove stores that fail hard constraints.

Example:

```text
Store A → inactive       → REMOVE
Store B → no inventory   → REMOVE
Store C → closed         → REMOVE
Store D → eligible       → KEEP
Store E → eligible       → KEEP
```

---

# 18. Hard Constraints vs Soft Constraints

This distinction is extremely important.

## Hard Constraints

A candidate either satisfies them or doesn't.

Examples:

```text
Store is open
Store accepts orders
Store delivers to customer
Inventory available
Store active
```

If false:

```text
Candidate = INVALID
```

---

## Soft Constraints

These affect ranking.

Examples:

```text
distance
ETA
rating
preparation time
reliability
delivery cost
```

A store can be worse in one category but better overall.

---

# 19. Delivery Zone Validation

A store may only deliver to certain areas.

For example:

```text
Store A
Delivery radius = 5 km
```

Customer:

```text
Distance = 7 km
```

Therefore:

```text
Store A = INVALID
```

A more advanced implementation can use:

```text
polygon-based delivery zones
```

rather than simple radius checks.

---

# 20. Customer Distance

Distance can be calculated using:

```text
Haversine distance
```

Conceptually:

```text
distance =
Haversine(
    storeLatitude,
    storeLongitude,
    customerLatitude,
    customerLongitude
)
```

This gives geographic distance.

However:

```text
Geographic Distance
```

is not necessarily:

```text
Road Distance
```

For production ETA, routing information is preferable.

---

# 21. Driver Availability

Store matching can also depend on driver availability.

Suppose:

```text
Store A
Nearby drivers = 0

Store B
Nearby drivers = 5
```

Even if Store A is closer, Store B may produce a faster delivery.

Therefore:

```text
Store Matching
```

and:

```text
Driver Matching
```

should eventually work together.

---

# 22. Store Capacity

Stores can become overloaded.

Example:

```text
Store A

Current active orders = 45
Maximum capacity = 50
```

Store A still technically accepts orders, but adding another order may increase preparation time significantly.

Therefore:

```text
capacity utilization
```

can become a scoring feature.

Example:

```text
utilization =
activeOrders / maxCapacity
```

---

# 23. Inventory Validation

Inventory should be checked against the entire cart.

Example:

```text
Cart:

Burger × 2
Coke × 1
Fries × 1
```

Store:

```text
Burger = 2
Coke = 10
Fries = 0
```

Result:

```text
FULL_FULFILLMENT = false
```

This candidate may still participate in a multi-store fulfillment plan.

---

# 24. Store Preparation Time

Preparation time can be:

```text
Static:

preparationTime = 15 minutes
```

or dynamically calculated:

```text
Preparation Time =
base preparation time
+
current queue delay
+
order complexity
```

Example:

```text
Base = 10 min
Queue = 15 min
Complexity = 5 min

Total = 30 min
```

---

# 25. Candidate Feature Vector

After filtering, construct features.

Example:

```text
distance = 2.4 km

roadDistance = 3.1 km

preparationTime = 12 min

driverETA = 6 min

storeRating = 4.6

reliability = 0.95

capacityUtilization = 0.40

inventoryCompleteness = 1.0

deliveryCost = ₹35
```

These features become the input to the ranking system.

---

# 26. Store Scoring

A simple scoring model could be:

```text
score =
    distanceScore
  + etaScore
  + preparationScore
  + reliabilityScore
  + ratingScore
  + costScore
```

Each component should be normalized.

Otherwise:

```text
distance = 3.2
rating = 4.8
```

would not be directly comparable.

---

# 27. Weighted Scoring Model

For example:

```text
score =
    0.30 × ETA
  + 0.20 × preparation
  + 0.15 × distance
  + 0.15 × reliability
  + 0.10 × rating
  + 0.10 × cost
```

The actual weights should be configurable.

For example:

```java
StoreMatchingWeights {

    etaWeight;

    preparationWeight;

    distanceWeight;

    reliabilityWeight;

    ratingWeight;

    costWeight;
}
```

This allows business rules to change without rewriting the algorithm.

---

# 28. ETA-Based Store Ranking

The most important metric may eventually become:

```text
Expected Delivery ETA
```

For example:

```text
Store A
Preparation = 30 min
Delivery = 10 min

ETA = 40 min
```

```text
Store B
Preparation = 10 min
Delivery = 18 min

ETA = 28 min
```

Store B wins despite being farther away.

---

# 29. Delivery Cost

Cost can also affect ranking.

Example:

```text
Store A
Delivery fee = ₹20

Store B
Delivery fee = ₹50
```

Depending on the business strategy, the engine may prefer:

```text
lowest cost
```

or:

```text
fastest delivery
```

or:

```text
best overall customer experience
```

This should be configurable.

---

# 30. Customer Experience Score

The system may eventually calculate:

```text
customerExperienceScore
```

based on:

```text
ETA
delivery fee
store rating
historical reliability
item availability
```

For example:

```text
CX Score =
0.40 × ETA
+
0.20 × reliability
+
0.15 × rating
+
0.15 × availability
+
0.10 × cost
```

---

# 31. Store Reliability Score

Historical performance can be used.

Example:

```text
Store A

On-time rate = 98%
Cancellation rate = 1%
Average preparation delay = 2 min
```

versus:

```text
Store B

On-time rate = 75%
Cancellation rate = 8%
Average preparation delay = 12 min
```

Store A may be preferable even if Store B is slightly closer.

---

# 32. Dynamic Store Ranking

The ranking should be dynamic.

At:

```text
12:00 PM
```

Store A may be best.

At:

```text
1:30 PM
```

Store B may become best because Store A has become overloaded.

Therefore:

```text
Store Ranking
```

should use real-time state.

---

# 33. The Best Store Is Not Always the Nearest Store

This is the fundamental principle.

Instead of:

```text
nearestStore()
```

we want:

```text
bestStore =
argmax(
    eligibility,
    inventory,
    ETA,
    preparation,
    reliability,
    cost,
    capacity,
    customerExperience
)
```

---

# 34. Multi-Store Order Matching

Now consider a more difficult case.

Customer orders:

```text
Pizza
Burger
Ice Cream
```

No single store has everything.

Possible solution:

```text
Store A
Pizza

Store B
Burger

Store C
Ice Cream
```

But this creates:

```text
multiple pickups
multiple preparations
multiple delivery operations
```

Therefore the engine should first attempt:

```text
Single Store Fulfillment
```

and only consider:

```text
Multi-Store Fulfillment
```

when necessary or beneficial.

---

# 35. Single-Store vs Multi-Store Order

Preferred strategy:

```text
Option 1
One store
```

If impossible:

```text
Option 2
Two stores
```

If still impossible:

```text
Option 3
Three stores
```

The engine should not automatically split every order.

---

# 36. Cart Validation

Represent the cart as:

```java
class OrderItem {

    UUID productId;

    int quantity;
}
```

Example:

```text
Cart
----------------
Pizza × 1
Burger × 2
Coke × 2
```

For every store:

```text
checkInventory(store, cart)
```

Result:

```text
FULL
PARTIAL
NONE
```

---

# 37. Store Combination Generation

Suppose:

```text
Store A → Pizza + Coke
Store B → Burger + Coke
Store C → Pizza + Burger
```

Possible combinations:

```text
A
B
C
A + B
A + C
B + C
A + B + C
```

The engine can evaluate each possible fulfillment plan.

---

# 38. Combination Explosion Problem

If there are:

```text
100 stores
```

the number of possible combinations becomes enormous.

We therefore cannot simply generate:

```text
every possible combination
```

This is a combinatorial optimization problem.

---

# 39. Optimized Combination Search

Use restrictions such as:

```text
maximum stores = 2
```

or:

```text
maximum stores = 3
```

Also prioritize stores that have:

```text
high inventory coverage
```

Example:

```text
Store A covers 80%
Store B covers 70%
Store C covers 20%
```

Evaluate A and B first.

---

# 40. Fulfillment Strategy

The engine should optimize:

```text
number of stores
+
total preparation time
+
total travel time
+
delivery cost
+
customer ETA
```

Example:

```text
Plan A

Store A
100% cart

ETA = 35 min
Cost = ₹40
```

versus:

```text
Plan B

Store A = 60%
Store B = 40%

ETA = 50 min
Cost = ₹80
```

Plan A wins.

---

# 41. Order Splitting

If splitting is required:

```text
Original Order
      |
      +---- Fulfillment A
      |       Store A
      |
      +---- Fulfillment B
              Store B
```

Each fulfillment can have:

```text
store
items
preparation status
driver
pickup location
delivery status
```

---

# 42. Driver Assignment Impact

Multi-store fulfillment changes driver assignment.

Single store:

```text
Store
  |
Driver
  |
Customer
```

Multi-store:

```text
Store A
   |
   +---- Driver
   |
Store B
   |
   +---- Driver
   |
Customer
```

Alternatively, one driver may perform:

```text
Store A
   ↓
Store B
   ↓
Customer
```

depending on the routing and business rules.

---

# 43. Store + Driver Joint Optimization

Eventually the engine should not optimize stores independently.

It should evaluate:

```text
Store
+
Driver
+
Route
+
ETA
```

Example:

```text
Store A
Nearest driver = 2 km
Preparation = 10 min

Store B
Nearest driver = 0.5 km
Preparation = 20 min
```

The final decision depends on:

```text
store preparation
+
driver availability
+
driver ETA
+
customer ETA
```

Therefore:

```text
Store Matching
        |
        v
Driver Matching
        |
        v
Delivery Optimization
```

---

# 44. Production Matching Pipeline

A production architecture can be:

```text
Order Service
     |
     v
Multi-Store Matching Engine
     |
     +----------------------+
     |                      |
     v                      v
Redis GEO              Inventory Service
     |                      |
     +----------+-----------+
                |
                v
        Candidate Generator
                |
                v
        Eligibility Filter
                |
                v
        Feature Calculator
                |
                v
          Ranking Engine
                |
                v
        Fulfillment Plan
```

---

# 45. Java Matching Engine

Create a dedicated service:

```java
@Service
public class StoreMatchingEngine {

    public MatchingResult match(MatchingRequest request) {

        // 1. Find candidates
        // 2. Filter candidates
        // 3. Build features
        // 4. Score candidates
        // 5. Rank candidates
        // 6. Return best fulfillment plan
    }
}
```

The engine should orchestrate rather than contain every business rule itself.

---

# 46. Store Candidate Service

Responsible for:

```text
Finding geographically relevant stores
```

Example:

```java
public interface StoreCandidateService {

    List<StoreCandidate> findCandidates(
        double latitude,
        double longitude,
        double radiusKm
    );
}
```

Implementation:

```text
Redis GEO
```

---

# 47. Store Eligibility Service

Responsible for hard constraints.

```java
public interface StoreEligibilityService {

    boolean isEligible(
        StoreCandidate store,
        MatchingRequest request
    );
}
```

Checks:

```text
open?
active?
accepting orders?
delivery zone?
inventory?
capacity?
```

---

# 48. Store Scoring Service

Responsible only for ranking.

```java
public interface StoreScoringService {

    double score(StoreFeatures features);
}
```

This allows different implementations:

```text
RuleBasedStoreScoringService
```

later:

```text
MLStoreScoringService
```

---

# 49. Matching Engine Orchestrator

The orchestrator coordinates everything:

```text
MatchingEngine
       |
       +-- CandidateService
       |
       +-- EligibilityService
       |
       +-- InventoryService
       |
       +-- ETAService
       |
       +-- PricingService
       |
       +-- ScoringService
       |
       +-- FulfillmentPlanner
```

This follows:

```text
Single Responsibility
Dependency Inversion
Open/Closed Principle
```

and makes the system easier to extend.

---

# 50. Matching Request

Example:

```java
public class MatchingRequest {

    UUID orderId;

    UUID customerId;

    double customerLatitude;

    double customerLongitude;

    List<OrderItem> items;

    DeliveryPriority priority;
}
```

---

# 51. Matching Response

Example:

```java
public class MatchingResult {

    UUID storeId;

    List<OrderItem> fulfilledItems;

    double score;

    double distanceKm;

    int preparationTimeMinutes;

    int estimatedDeliveryMinutes;

    BigDecimal deliveryFee;
}
```

For multi-store:

```java
class FulfillmentPlan {

    List<StoreFulfillment> fulfillments;

    int totalEta;

    BigDecimal totalCost;

    double score;
}
```

---

# 52. Example Matching Algorithm

Pseudo-code:

```text
match(order):

    candidates =
        findNearbyStores(order.customerLocation)

    eligible =
        filterEligible(candidates, order)

    for each store:

        inventory =
            validateInventory(store, order)

        if inventory is invalid:
            continue

        features =
            buildFeatures(store, order)

        score =
            calculateScore(features)

        rankedCandidates.add(store, score)

    sort candidates by score

    bestSingleStore =
        highest ranked candidate

    if bestSingleStore fully satisfies order:

        return bestSingleStore

    else:

        generate multi-store plans

        evaluate plans

        return best plan
```

---

# 53. Example Java Implementation

A simple first implementation:

```java
@Service
@RequiredArgsConstructor
public class StoreMatchingEngine {

    private final StoreCandidateService candidateService;
    private final StoreEligibilityService eligibilityService;
    private final InventoryService inventoryService;
    private final StoreFeatureService featureService;
    private final StoreScoringService scoringService;

    public MatchingResult match(MatchingRequest request) {

        List<StoreCandidate> candidates =
                candidateService.findCandidates(
                        request.customerLatitude(),
                        request.customerLongitude(),
                        5.0
                );

        List<ScoredStore> scoredStores = new ArrayList<>();

        for (StoreCandidate candidate : candidates) {

            if (!eligibilityService.isEligible(candidate, request)) {
                continue;
            }

            InventoryResult inventory =
                    inventoryService.check(
                            candidate.storeId(),
                            request.items()
                    );

            if (!inventory.isFullFulfillment()) {
                continue;
            }

            StoreFeatures features =
                    featureService.build(
                            candidate,
                            request,
                            inventory
                    );

            double score =
                    scoringService.score(features);

            scoredStores.add(
                    new ScoredStore(candidate, score)
            );
        }

        return scoredStores.stream()
                .max(Comparator.comparingDouble(ScoredStore::score))
                .map(this::toResult)
                .orElseThrow(
                    () -> new NoStoreAvailableException(
                        "No eligible store found"
                    )
                );
    }
}
```

This is intentionally simple.

The architecture can later evolve without replacing the entire system.

---

# 54. Real-Time Store State

Store state changes continuously.

For example:

```text
10:00 AM
Store A capacity = 30%

10:30 AM
Store A capacity = 80%

11:00 AM
Store A capacity = 100%
```

The matching engine should use current state whenever possible.

---

# 55. Store Events

Useful events include:

```text
STORE_OPENED
STORE_CLOSED
STORE_BUSY
STORE_AVAILABLE
STORE_INVENTORY_CHANGED
STORE_CAPACITY_CHANGED
STORE_PREPARATION_TIME_CHANGED
STORE_ORDER_ACCEPTED
STORE_ORDER_REJECTED
```

These can be published through Kafka.

---

# 56. WebSocket Store Updates

The dashboard can subscribe to:

```text
/topic/stores
```

Example event:

```json
{
  "storeId": "store-123",
  "status": "BUSY",
  "capacityUtilization": 0.82,
  "estimatedPreparationMinutes": 25
}
```

The dashboard can immediately update store markers.

---

# 57. Kafka Integration

Kafka can be used for asynchronous events:

```text
OrderCreated
     |
     v
Kafka
     |
     +---- Matching Service
     |
     +---- Inventory Service
     |
     +---- Analytics
     |
     +---- Notification Service
```

Example topic:

```text
order.created
```

The matching service consumes the event and starts store matching.

---

# 58. Caching Strategy

Some information changes slowly.

Examples:

```text
store name
store address
store rating
store delivery zones
```

These can be cached.

Fast-changing information:

```text
inventory
capacity
preparation time
accepting orders
```

should have a strategy appropriate to its freshness requirements.

---

# 59. Database Responsibilities

PostgreSQL should remain the source of truth for persistent business data.

Examples:

```text
stores
store_products
store_inventory
store_hours
delivery_zones
orders
order_items
store_orders
```

Database:

```text
durable state
```

---

# 60. Redis Responsibilities

Redis should handle high-speed operational queries.

Examples:

```text
Store GEO locations
Current store availability
Temporary capacity state
Short-lived matching cache
Driver GEO locations
```

Redis:

```text
fast operational state
```

Do not treat Redis GEO as the permanent source of truth for stores.

---

# 61. Matching Failure Handling

Matching can fail because:

```text
No nearby stores
No store has inventory
All stores closed
All stores overloaded
No delivery zone available
Inventory changed during matching
```

The system should return a meaningful failure state.

Example:

```text
STORE_NOT_AVAILABLE
```

instead of:

```text
500 Internal Server Error
```

---

# 62. Fallback Strategy

If no store is found within:

```text
3 km
```

expand:

```text
5 km
```

then:

```text
8 km
```

then:

```text
10 km
```

If still no store:

```text
NO_FULFILLMENT_AVAILABLE
```

The customer-facing service can then provide an appropriate message.

---

# 63. Re-Matching

Matching should not happen only once.

Re-match when:

```text
Store rejects order
Inventory becomes unavailable
Store closes
Preparation time increases significantly
Driver unavailable
Order SLA at risk
```

Example:

```text
Store A selected
      |
      v
Store rejects order
      |
      v
Matching Engine
      |
      v
Store B
```

---

# 64. Dynamic Re-Ranking

Suppose:

```text
Store A
ETA = 25 min
```

Initially selected.

Five minutes later:

```text
Store A
ETA = 50 min
```

while:

```text
Store B
ETA = 28 min
```

The system can evaluate whether re-assignment is worthwhile.

However, after preparation has started, switching stores may be more expensive than accepting the delay.

Therefore re-ranking should be controlled by business rules.

---

# 65. Machine Learning Enhancement

The first implementation should usually be:

```text
Rule-Based Matching
```

Then historical data can be collected.

Eventually:

```text
ML Store Ranking
```

can replace or augment the rule-based scorer.

---

# 66. ML Feature Engineering

Potential features:

### Geographic

```text
storeDistance
roadDistance
customerRegion
storeRegion
```

### Store

```text
storeRating
historicalAcceptanceRate
historicalCancellationRate
averagePreparationTime
```

### Real-Time

```text
currentOrders
capacityUtilization
currentPreparationDelay
```

### Order

```text
itemCount
orderValue
foodCategory
priority
```

### Time

```text
hour
dayOfWeek
holiday
```

### Delivery

```text
driverAvailability
driverETA
traffic
```

---

# 67. ML-Based Store Ranking

The model can predict:

```text
P(successful fulfillment)
```

or:

```text
expected delivery time
```

or:

```text
probability of late delivery
```

For example:

```text
Store A
Predicted ETA = 32 min
Late probability = 12%

Store B
Predicted ETA = 28 min
Late probability = 25%
```

A combined ranking model may still select Store A.

---

# 68. Production ML Architecture

A scalable architecture could be:

```text
Order Service
      |
      v
Matching Engine
      |
      v
Feature Builder
      |
      v
Feature Store
      |
      v
ML Model Server
      |
      v
Store Ranking
      |
      v
Fulfillment Plan
```

Model options can include:

```text
XGBoost
Random Forest
Neural Networks
Gradient Boosting
Deep Learning
```

Start simple and move to ML when sufficient historical data exists.

---

# 69. Complete Order Flow

```text
Customer Places Order
        |
        v
Order Service
        |
        v
Order Created
        |
        v
Kafka: order.created
        |
        v
Multi-Store Matching Engine
        |
        v
Find Nearby Stores
        |
        v
Filter Stores
        |
        v
Validate Inventory
        |
        v
Calculate ETA
        |
        v
Calculate Cost
        |
        v
Rank Stores
        |
        v
Select Fulfillment Plan
        |
        v
Create Store Order
```

---

# 70. Complete Multi-Store Flow

```text
Customer Cart
     |
     v
Find Stores
     |
     +---- Store A → 70% items
     |
     +---- Store B → 30% items
     |
     v
Create Fulfillment Plan
     |
     +---- Fulfillment A
     |
     +---- Fulfillment B
     |
     v
Assign Drivers
     |
     v
Track Pickups
     |
     v
Route Optimization
     |
     v
Customer Delivery
```

---

# 71. Complete Store + Driver Flow

This connects the Multi-Store Matching Engine with the Driver Matching Engine.

```text
Order
 |
 v
Store Matching
 |
 v
Best Store
 |
 v
Find Nearby Drivers
 |
 v
Driver Eligibility
 |
 v
Driver ETA
 |
 v
Driver Ranking
 |
 v
Driver Assignment
 |
 v
Store Pickup
 |
 v
Customer Delivery
```

For multi-store:

```text
Order
 |
 v
Store Matching
 |
 +---- Store A
 |       |
 |       v
 |    Driver A
 |
 +---- Store B
         |
         v
      Driver B
```

---

# 72. Production Considerations

A production implementation should consider:

### Concurrency

Two customers may attempt to purchase the last item simultaneously.

Inventory reservation must therefore be atomic.

---

### Idempotency

The same order event may be delivered more than once.

Use:

```text
orderId
+
matchingAttemptId
```

to make processing idempotent.

---

### Timeouts

Matching should have a strict timeout.

Example:

```text
Matching timeout = 2 seconds
```

The exact limit depends on system requirements.

---

### Observability

Track:

```text
matching latency
candidate count
eligible candidate count
selected store
rejected candidates
matching failure rate
re-match count
```

---

### Explainability

The engine should ideally record why a store won.

Example:

```json
{
  "storeId": "store-A",
  "score": 0.91,
  "reason": {
    "eta": "best",
    "inventory": "complete",
    "reliability": "high",
    "distance": "acceptable"
  }
}
```

This is extremely useful for debugging.

---

# 73. Implementation Phases

Do not implement the entire system at once.

## Phase 1 — Basic Store Matching

Implement:

```text
Store
Customer
Distance
Nearby Store Search
```

Flow:

```text
Customer
   |
   v
Nearby Stores
   |
   v
Nearest Eligible Store
```

---

## Phase 2 — Redis GEO

Add:

```text
GEOADD
GEOSEARCH
```

for fast store discovery.

---

## Phase 3 — Inventory Matching

Add:

```text
Store → Product → Inventory
```

and validate the complete cart.

---

## Phase 4 — Eligibility Engine

Add:

```text
Store hours
Store status
Delivery zones
Capacity
Inventory
```

---

## Phase 5 — Scoring Engine

Add:

```text
ETA
Preparation
Distance
Reliability
Rating
Cost
```

and weighted scoring.

---

## Phase 6 — Fulfillment Planner

Add:

```text
Single-store fulfillment
Multi-store fulfillment
Order splitting
```

---

## Phase 7 — Driver Integration

Connect:

```text
Store Matching
        |
        v
Driver Matching
```

---

## Phase 8 — Real-Time Updates

Add:

```text
Kafka
WebSocket
Redis
```

for real-time store and driver state.

---

## Phase 9 — Dynamic Re-Matching

Support:

```text
Store rejection
Inventory changes
Capacity changes
ETA degradation
```

---

## Phase 10 — ML Ranking

Once sufficient historical data exists:

```text
Rule-Based Ranking
       |
       v
ML-Assisted Ranking
       |
       v
ML-Based Ranking
```

---

# 74. Final Architecture

The complete system becomes:

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
                     KAFKA EVENT
                            |
                            v
              MULTI-STORE MATCHING ENGINE
                            |
          +-----------------+-----------------+
          |                 |                 |
          v                 v                 v
      Redis GEO        Inventory Service   Store Service
          |                 |                 |
          +-----------------+-----------------+
                            |
                            v
                  CANDIDATE GENERATOR
                            |
                            v
                  ELIGIBILITY ENGINE
                            |
                            v
                   FEATURE BUILDER
                            |
               +------------+------------+
               |                         |
               v                         v
          ETA SERVICE               PRICING
               |                         |
               +------------+------------+
                            |
                            v
                     RANKING ENGINE
                            |
                            v
                  FULFILLMENT PLANNER
                            |
              +-------------+-------------+
              |                           |
              v                           v
       SINGLE STORE                 MULTI STORE
              |                           |
              +-------------+-------------+
                            |
                            v
                    DRIVER MATCHING
                            |
                            v
                    DRIVER ASSIGNMENT
                            |
                            v
                     PICKUP / DELIVERY
                            |
                            v
                    CUSTOMER DELIVERY
```

---

# 75. Final Success Criteria

The Multi-Store Matching Engine is successful when it can:

```text
✓ Find nearby stores quickly

✓ Filter unavailable stores

✓ Validate delivery zones

✓ Validate inventory

✓ Consider store capacity

✓ Consider preparation time

✓ Calculate delivery ETA

✓ Consider driver availability

✓ Rank stores intelligently

✓ Select the best single store

✓ Detect when a single store cannot fulfill the order

✓ Generate optimized multi-store plans

✓ Minimize unnecessary store splitting

✓ Re-match when fulfillment fails

✓ Integrate with driver matching

✓ Support real-time state

✓ Support future ML ranking

✓ Explain why a store was selected
```

---

# 76. The Key Architectural Principle

The most important idea is:

```text
Do NOT build:

findNearestStore()
```

Build:

```text
findCandidateStores()
        |
        v
filterEligibleStores()
        |
        v
validateInventory()
        |
        v
calculateFeatures()
        |
        v
predictETA()
        |
        v
calculateCost()
        |
        v
rankStores()
        |
        v
buildFulfillmentPlan()
        |
        v
selectBestPlan()
```

The architecture should therefore evolve from:

```text
Nearest Store
```

to:

```text
Best Store
```

and eventually:

```text
Best Fulfillment Plan
```

where:

```text
Best Fulfillment Plan
=
Store Selection
+
Inventory
+
Preparation Time
+
Driver Availability
+
Route
+
ETA
+
Cost
+
Reliability
+
Customer Experience
```

This gives us a clean separation between **candidate discovery**, **eligibility**, **prediction**, **ranking**, and **fulfillment planning**.

It also makes the Multi-Store Matching Engine compatible with the previously designed **Driver Matching Engine**, **ETA Prediction**, **Redis GEO**, **WebSocket tracking**, **Kafka events**, and **real-time delivery dashboard**.