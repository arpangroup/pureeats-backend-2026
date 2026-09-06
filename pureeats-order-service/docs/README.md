# Pre-Order Validation & Delivery Assignment

This document covers everything that happens **between a customer adding an item to their cart and
a delivery partner being assigned to the resulting order** — every validation rule that can block or
adjust an order, and the two services that orchestrate the process. It deliberately stops at
"order created / rider assigned" and does **not** cover the later order-status-transition flow
(accept → prepare → out-for-delivery → delivered → ...), which is a separate concern
(`OrderStatusTransitions`, `AdminOrderController`).

Two diagrams accompany this file:

- **[checkout-validation-map.html](checkout-validation-map.html)** — the full class-level map: cart
  validation rules, address/distance validation, coupon validation (summary), pricing, order
  placement, and delivery-partner assignment.
- **[coupon-validation-map.html](coupon-validation-map.html)** — a focused, zoomed-in diagram of
  just the coupon-validation rule chain and discount calculators.

Everything here was read directly from source in `pureeats-order-service` and `pureeats-catalog-service`
(with entities in `domain`) — class names, method signatures and field lists are accurate as of this
writing, not paraphrased from memory.

## Where this code actually lives

There is **no persisted `Cart` entity** — the client owns cart state and sends a plain list of
`{itemId, quantity, selectedAddonIds}` lines on every call. "Cart validation" is really "is this
client-supplied list of lines still orderable right now."

The pipeline is split across three modules, wired together by direct Spring bean injection (this is a
single multi-module deployable, not separate microservices talking over HTTP):

| Concern | Lives in |
|---|---|
| Cart validation rule engine | `pureeats-order-service` (`com.pureeats.order.service.cartvalidation`) |
| Order placement orchestration | `pureeats-order-service` (`com.pureeats.order.service.OrderService`) |
| Delivery-partner assignment | `pureeats-order-service` (`com.pureeats.order.service.DeliveryOrderService`) |
| Coupon / discount validation | `pureeats-catalog-service` (`com.pureeats.catalog.service.CouponService`, `discount/*`) |
| Distance calculation | `pureeats-geo-service` (`com.pureeats.geo.*`) |
| Restaurant open/closed logic | `pureeats-catalog-service` (`RestaurantOpenStatusService`) |

`pureeats-order-service` depends on `pureeats-catalog-service`, `pureeats-geo-service` and
`pureeats-user-service` beans directly (`CouponService`, `DistanceCalculator`,
`RestaurantRepository`, `AddressRepository`, ...). `pureeats-catalog-service` also depends on
`pureeats-geo-service` directly (`RestaurantService.checkDeliveryArea`/`findNearby`) — distance math
was extracted into its own module (mirroring how `domain` already works) so it's shared
infrastructure both services depend on, not something order-service reaches through catalog-service
for. See **[Roadmap / future considerations](#roadmap--future-considerations)** below for why.

## The two entry points, one rule engine

There are exactly two places a cart gets evaluated, and **both run through the same rule beans** so
they can never disagree about what's orderable:

1. **`CartController.validate` → `CartValidationService.validate(request, userId)`**
   `POST /api/v1/cart/validate` — the live, non-throwing check the Cart page polls to grey out
   unavailable items and show an up-to-date price. Doesn't know the payment mode yet (chosen later,
   on Checkout) and may not have an address yet.

2. **`OrderController` (place order) → `OrderService.placeOrder` → `CartValidationService.assertPlaceable(...)`**
   The fail-fast server-side gate — runs the *exact same* rule beans, but throws
   `BadRequestException` on the very first issue found instead of collecting them all. This always
   has a payment mode and (for a `DELIVERY` order) a resolved address.

Both call into `CartValidationService`, which collects every Spring bean implementing
`CartValidationRule` and runs all of them against a `CartValidationContext`:

```java
public record CartValidationContext(
    Restaurant restaurant, List<CartLine> lines, Map<Long, Item> resolvedItems,
    DeliveryType deliveryType, BigDecimal distanceKm, String paymentMode,
    Long userId, BigDecimal rawItemTotal
)
```

Rules **never throw and never short-circuit** each other — `evaluateAll` runs every rule and merges
the results, so a customer with one unavailable item among five still sees all five evaluated in the
live preview. `assertPlaceable` then just throws on `issues.get(0)`.

## Cart validation rules (`CartValidationRule` implementations)

Each is a `@Component` implementing `List<CartIssue> evaluate(CartValidationContext context)`,
auto-collected by Spring into `CartValidationService`'s `List<CartValidationRule> rules` — adding a
new rule is a new class, never an edit to an if/else chain.

| Rule | Blocks when | Key dependency |
|---|---|---|
| `RestaurantAvailabilityRule` | Restaurant deactivated, not accepting orders, or closed per its posted schedule | `RestaurantOpenStatusService` + `RestaurantScheduleCodec` |
| `ItemAvailabilityRule` | Item deleted, or deactivated/moved off *this* restaurant's menu | — |
| `ItemStockRule` | `stockQuantity <= 0` (out of stock) or `< quantity` requested (untracked/`null` stock is skipped) | — |
| `MinimumOrderAmountRule` | Raw item total is below the restaurant's `minOrderPrice` | — |
| `DeliveryRadiusRule` | For `DELIVERY` orders only: `distanceKm > restaurant.deliveryRadius` | distance passed in from `OrderPricingService` → `DistanceCalculator` |
| `AddonSelectionRule` | A selected addon isn't actually offered on that item (checked via `AddonCategoryItemRepository`) | `AddonRepository`, `AddonCategoryItemRepository` |
| `PaymentMethodRule` | `paymentMode == COD` but `restaurant.isAcceptCod == false` (only fires once a payment mode is known, i.e. at placeOrder time) | — |
| `OrderFrequencyRule` | User has placed ≥`maxOrders` (default 3) orders in the last `windowMinutes` (default 10) — anti-fraud rate limit | `OrderRepository` |

`RestaurantAvailabilityRule` is the one rule that defers entirely to another service
(`RestaurantOpenStatusService.compute(restaurant, schedule)`) rather than checking a field directly —
that's the same day-aware, multi-slot schedule computation the customer-facing restaurant-detail API
uses, so the checkout gate can never disagree with what the customer was shown on the restaurant page.

## Address / distance / delivery-range validation

Distance math is centralized behind one interface, `DistanceCalculator`, living in its own module
(`pureeats-geo-service`, package `com.pureeats.geo.distance`) rather than inside catalog-service, so every
caller — pricing, the delivery-radius gate, and the pre-cart "can this restaurant even deliver to
me" check — always agrees on the same number:

```java
public interface DistanceCalculator {
    BigDecimal distanceKm(String lat1, String lng1, String lat2, String lng2); // never throws
}
```

| Implementation | Notes |
|---|---|
| `HaversineDistanceCalculator` | Default — great-circle distance, Earth radius 6371km |
| `EuclideanDistanceCalculator` | Flat-plane approximation (111.32 km/degree) — demo-only alternative |
| `GoogleDistanceMatrixCalculator` | Real road distance via Google's Distance Matrix API; falls back to Haversine if no API key or the call fails |

The active implementation is chosen purely by the `pureeats.distance.provider` property
(`haversine` / `euclidean` / `google`) in `DistanceCalculatorConfig`, the same
`@ConditionalOnProperty`-per-bean pattern used to pick the active email/SMS provider in
`pureeats-notification-service`.

Two distinct address/distance checks exist, at two different points in the flow:

1. **Before the cart** — `RestaurantService.checkDeliveryArea(restaurantId, lat, lng)`
   (`POST /{id}/check-delivery-area`) — "is my address even inside this restaurant's radius" shown on
   the restaurant-detail page, before anything is added to a cart.
2. **At checkout** — `DeliveryRadiusRule` (above) — the actual hard gate that blocks `placeOrder`
   when the resolved delivery address is outside `restaurant.deliveryRadius`.

Both ultimately call the same `DistanceCalculator` bean. `OrderPricingService.computeDeliveryCharge`
also uses distance, but to *price* delivery (flat vs. distance-tiered "dynamic" charge), not to block
the order — a restaurant can charge more for a farther delivery without necessarily refusing it,
right up until the radius rule kicks in.

## Coupon validation

Covered in full detail, with its own diagram, in **[coupon-validation-map.html](coupon-validation-map.html)**.
Summary: `CouponService.validate(code, restaurantId, orderAmount, isFirstOrder)` is the single private
gate consulted by both `preview()` (lenient, no usage recorded — used by the live cart preview) and
`recordUsage()` (atomic re-validate + record — used by `OrderService.placeOrder`). It checks, in
order: **exists & active → not expired → restaurant scope → usage limit → minimum order amount →
first-order-only**. The actual discount amount is then computed by whichever `DiscountCalculator`
bean matches the coupon's `DiscountType` (`AMOUNT` / `PERCENTAGE` / `FREE_DELIVERY`) — a strategy
pattern, the same shape as `CartValidationRule`. Only one coupon code per order; there is no stacking.

## Order placement orchestration (`OrderService.placeOrder`)

`OrderService.placeOrder(userId, PlaceOrderRequest request)` is the single checkout orchestrator.
Sequence:

1. Load `Restaurant` (404 if missing).
2. Load `Address`; assert `address.userId == userId` (`ForbiddenException` otherwise).
3. Compute `distanceKm` via `OrderPricingService.distanceKm(...)` (`null` for `SELF_PICKUP`).
4. **`cartValidationService.assertPlaceable(...)`** — runs the entire rule pipeline above; throws on
   the first issue (closed restaurant, unavailable item, out of stock, outside delivery radius, below
   minimum order, disallowed addon, COD not accepted, ordering too frequently).
5. Build the `Order` entity skeleton (unique order ID, address snapshot, payment mode, delivery type,
   delivery PIN, driver tip).
6. Resolve item/addon lines and sum `itemTotal`.
7. If a coupon code is present: compute `isFirstOrder` (order-service owns `OrderRepository`, so it's
   the one that knows this) and call **`couponService.recordUsage(...)`**.
8. Compute `tax`, `restaurantCharge`, and `deliveryCharge` via `OrderPricingService`; sum into
   `payable`.
9. Persist the pricing breakdown as JSON on the order (`PricingBreakdown` record) for later audit/display.
10. Set the initial status: `RESTAURANT_ACCEPTED` if the restaurant is auto-acceptable, else `PLACED`.
11. Save the `Order`, log the status transition, persist `OrderItem` + `OrderItemAddon` rows per line.
12. If `paymentMode == WALLET`, debit the customer's wallet via `WalletService.debit(...)`.
13. Notify every `RestaurantUser` owner of the new order.

This is the last step covered here — everything after (`RESTAURANT_ACCEPTED`/`PLACED` progressing
through the rest of the order-status machine) is out of scope for this document.

## Delivery partner assignment (`DeliveryOrderService`)

There is **no automatic "nearest rider" matching algorithm** — `DeliveryGuyDetail` has `isOnline`,
`lastLat`, `lastLng` fields, but nothing in the assignment logic reads them. Assignment is either the
rider pulling a job, or an admin pushing one:

- **`availableOrders()`** — the rider's "job board": orders in `RESTAURANT_ACCEPTED` or
  `READY_FOR_PICKUP` status, `deliveryType == DELIVERY`, with no existing `AcceptDelivery` row yet.

- **`acceptToDeliver(riderUserId, orderId)`** — rider self-assigns ("pull"):
  1. Load the caller's `DeliveryGuyDetail` rider profile (404/`Forbidden` if they don't have one).
  2. **Guard: not already assigned** — reject if an `AcceptDelivery` row already exists for this order.
  3. **Guard: concurrent-delivery limit** — reject if the rider's count of incomplete deliveries
     (`AcceptDelivery` rows with `isComplete = false`) is `>= rider.maxAcceptDeliveryLimit`.
  4. Create the `AcceptDelivery` row, transition the order to `RIDER_ASSIGNED`, notify the customer.

- **`assignDriverAsAdmin(adminUserId, orderId, riderUserId)`** — admin override ("push"): same
  not-already-assigned guard, but **skips the concurrent-limit check** (an explicit admin decision).
  Notifies both the customer and the newly-assigned rider.

`AcceptDelivery` is the actual assignment record — one row per order-to-rider assignment
(`orderId, userId (rider), customerId, isComplete`).

## Roadmap / future considerations

None of this exists in the codebase today — it's captured here (and as clearly-marked "proposed,
not implemented" boxes in **[checkout-validation-map.html](checkout-validation-map.html)**) purely
as a record of ideas worth revisiting later.

**More `CartValidationRule` implementations** — the 8 rules above cover today's needs, but the same
plug-in pattern (a new `@Component implements CartValidationRule`, nothing else changes) would fit:
`MaximumOrderAmountRule` (the inverse of the existing minimum), `MaxItemQuantityRule` (anti-bulk-buy),
`RestaurantCapacityRule` (block new orders once a kitchen's pending-order backlog crosses a
threshold), `DuplicateOrderRule` (reject an identical cart resubmitted within seconds — a
double-tap guard distinct from `OrderFrequencyRule`'s raw count), `AgeRestrictedItemRule`,
`UserOutstandingDuesRule` (block new COD orders for users with unpaid past COD dues),
`AllergenConflictRule` (the one that would *warn* rather than hard-block), and `ScheduledSlotRule`
(for pre-scheduled orders, validate the requested slot against the restaurant's *future* hours).

**A fuller `DistanceCalculator` API** — today it's a single `distanceKm(...)` method. It could grow
into something closer to a location-database API: `nearby(...)`, `topNNearest(...)`,
`isWithinRadius(...)`, `etaMinutes(...)` (real travel time, not straight-line), `boundingBox(...)`,
`geocode(...)`/`reverseGeocode(...)`, `distanceMatrix(...)` (batch, matching Google's real API
shape), and — the one that matters most — `nearestAvailableRider(pickup, candidateRiders)`, which
would finally put `DeliveryGuyDetail.isOnline/lastLat/lastLng` to use instead of leaving them read
by nothing, closing the "no proximity-based rider matching exists today" gap noted above.

**Three bigger initiatives**, roughly in the order they'd unlock each other:

1. ~~Extract the geo code into its own module, mirroring how `domain` already works.~~ **Done** —
   `DistanceCalculator` and its three implementations now live in `pureeats-geo-service`
   (`com.pureeats.geo.distance`), depended on directly by both `pureeats-order-service` and
   `pureeats-catalog-service`. This was pulled forward ahead of the next two items landing, since it's
   the shared foundation both would build on.
2. **A routing engine** (GraphHopper-style, or a hand-rolled Dijkstra/A* over a road graph) for real
   road-network distance and ETA instead of straight-line Haversine or a paid, rate-limited
   third-party API. Would become a new `DistanceCalculator` implementation in `pureeats-geo-service`
   and could eventually replace `GoogleDistanceMatrixCalculator` outright.
3. **Store delivery boundaries via polygon mapping** — replace the single `deliveryRadius` number
   (a circle) with an actual drawn shape per restaurant, checked with a point-in-polygon test
   instead of `distanceKm <= radius`. A circle can't account for rivers, highways, or one-way road
   networks; a polygon can, and could support per-zone delivery pricing (e.g. Zone A free, Zone B
   +₹20). Needs a spatial library (e.g. JTS) and an admin-side map UI to draw/edit the shapes —
   would also live in `pureeats-geo-service` once built.

## Key supporting types

| Type | Shape |
|---|---|
| `PlaceOrderRequest` (record) | `restaurantId, addressId, items: List<PlaceOrderItemRequest>, paymentMode, deliveryType, couponCode, orderComment, driverTipAmount` |
| `CartValidationRequest` (record) | `restaurantId, items, addressId, couponCode, deliveryType` |
| `CartIssue` (record) | `itemId` (null ⇒ whole-cart/restaurant-level issue), `reason` |
| `Coupon` (entity) | see coupon diagram |
| `AcceptDelivery` (entity) | `orderId, userId, customerId, isComplete` |
| `DeliveryGuyDetail` (entity) | `maxAcceptDeliveryLimit, isOnline, lastLat, lastLng, rating, vehicleNumber, ...` (location fields currently unused by assignment logic) |

## Class relationships at a glance

```
CartValidationService --o CartValidationRule (interface, 8 implementations, strategy pattern)
CartValidationRule ⟵ RestaurantAvailabilityRule, ItemAvailabilityRule, ItemStockRule,
                      MinimumOrderAmountRule, DeliveryRadiusRule, AddonSelectionRule,
                      PaymentMethodRule, OrderFrequencyRule

RestaurantAvailabilityRule --> RestaurantOpenStatusService, RestaurantScheduleCodec
DeliveryRadiusRule ..> distanceKm (computed upstream by OrderPricingService --> DistanceCalculator)

OrderPricingService --> DistanceCalculator (interface, @ConditionalOnProperty-selected)
DistanceCalculator ⟵ HaversineDistanceCalculator, EuclideanDistanceCalculator, GoogleDistanceMatrixCalculator

CouponService --o DiscountCalculator (interface, strategy pattern)
DiscountCalculator ⟵ FlatDiscountCalculator, PercentageDiscountCalculator, FreeDeliveryDiscountCalculator

OrderService --> CartValidationService (assertPlaceable), CouponService (recordUsage),
                 OrderPricingService, WalletService, AddressRepository, RestaurantRepository
OrderService produces --> Order, OrderItem, OrderItemAddon

DeliveryOrderService --> OrderService, AcceptDeliveryRepository, DeliveryGuyDetailRepository
DeliveryOrderService produces --> AcceptDelivery (links Order <-> rider)
```

---
*Generated from source in `pureeats-order-service` and `pureeats-catalog-service`, September 2026.*
