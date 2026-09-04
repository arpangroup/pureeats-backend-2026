# Restaurant Domain Architecture

A reference for how a `Restaurant` row relates to everything else in the catalog — categories,
items, addons, coupons, location, owner, media, audit trail — and, in detail, how opening hours are
stored and how "is this restaurant open right now" actually gets decided. A companion HTML version
with diagrams lives at [`RESTAURANT_DOMAIN_ARCHITECTURE.html`](RESTAURANT_DOMAIN_ARCHITECTURE.html).

Scope: `pureeats-backend-2026` (Spring Boot / JPA / MySQL-MariaDB), plus the two frontends that read
its API — `pureeats-admin-react-app-2026` and `pureeats-customer-react-app-2026`.

## 1. Where this lives in the codebase

This is a **modular monolith**, not literal microservices: several Maven modules, one deployable
Spring Boot application (`pureeats-app`, single JVM, single port).

| Module | Owns | Relevant to this doc |
|---|---|---|
| `domain` | Shared JPA entities used across modules | `Restaurant`, `RestaurantCategory`, `Location`, `Item`, `ItemCategory`, `Addon`, `AddonCategory`, `AddonCategoryItem`, `Coupon`, `RestaurantUser`, `DeliveryGuyRestaurant`, `RestaurantEarning`, `RestaurantPayout` |
| `pureeats-catalog-service` | Restaurants, menu, categories, locations, sliders, app config | `RestaurantService`, `RestaurantScheduleCodec`, `MenuService`, `RestaurantCategoryService`, `LocationService`, plus catalog-service-local entities `RestaurantCategoryRestaurant` and `RestaurantAuditLog` (not in `domain` — see §3.7) |
| `pureeats-order-service` | Cart validation, pricing, order lifecycle | `RestaurantAvailabilityRule` — one of the two places that decides "open now" |
| `pureeats-media-service` | File uploads | `MediaAsset` — backs restaurant cover + gallery images |
| `pureeats-user-service` | Auth, roles | Owns who may call the store-owner vs admin restaurant endpoints |
| `pureeats-app` | Wiring, `main()`, security config | Nothing domain-specific |

The **admin panel** (`pureeats-admin-react-app-2026`) and **customer app**
(`pureeats-customer-react-app-2026`) are separate SPAs that both call this one backend over REST.

## 2. The `restaurants` table

`domain/entity/Restaurant.java` — the row every other relationship in this doc hangs off. Grouped by
purpose (not the literal column order):

| Group | Columns |
|---|---|
| Identity | `id`, `name`, `slug`, `sku`, `description`, `contact_number`, `certificate` |
| Status | `is_active`, `is_accepted`, `is_featured`, `auto_acceptable`, `is_schedulable`, `is_notifiable` |
| Location (geo) | `latitude`, `longitude` (both `String`), `address`, `pincode`, `landmark` |
| Location (tag) | `location_id` — see §3.6, this is **not** a foreign key |
| Hours (legacy) | `opening_time`, `closing_time` — single daily window, see §4 |
| Hours (current) | `schedule_data` — JSON blob, the real weekly multi-slot schedule, see §4 |
| Pricing | `restaurant_charges`, `delivery_charges`, `commission_rate`, `min_order_price` |
| Delivery | `delivery_type` (0/1/2 → self-pickup/delivery/both), `delivery_radius`, `delivery_charge_type`, `base_delivery_charge`, `base_delivery_distance`, `extra_delivery_charge`, `extra_delivery_distance`, `delivery_time` (`String`, minutes) |
| Media | `image`, `placeholder_image` (gallery images live in `media_assets`, not on this row — see §3.5) |
| Misc | `is_accept_cod`, `rating`, `price_range` |

Nothing here is a JPA `@ManyToOne`/`@OneToMany` — every relationship in this codebase is a plain
`Long`/`Integer` id column plus a manually-written repository query. That's a deliberate, consistent
style choice across the whole domain model (see any entity above), not specific to `Restaurant`.

## 3. How a restaurant connects to everything else

| # | Related to | Mechanism | Cardinality | Notes |
|---|---|---|---|---|
| 3.1 | `RestaurantCategory` (cuisine tags — "Pizza", "Chinese") | Join table `restaurant_category_restaurant` (`RestaurantCategoryRestaurant` entity: `restaurant_id`, `restaurant_category_id`) | many-to-many | Written by `RestaurantService.replaceCategoryLinks()` on create/patch; read by `RestaurantCategoryService.restaurantsInCategory()` (`GET /restaurant-categories/{id}/restaurants` — this is the endpoint a "tap a cuisine chip" customer screen calls) and by `RestaurantService.categoryIdsFor()` for the admin edit form. |
| 3.2 | `Item` (menu items) | `Item.restaurantId` | one-to-many | A restaurant's menu is `items` filtered by this column. |
| 3.3 | `ItemCategory` ("Starters", "Desserts") | `Item.itemCategoryId` | many-to-one, **indirect** | `ItemCategory` has no `restaurant_id` at all — it's a global taxonomy shared by every restaurant, not restaurant-scoped. An item picks one category from the shared list. |
| 3.4 | `AddonCategory` / `Addon` ("Toppings" → "Extra cheese") | `AddonCategoryItem` join table (`addon_category_id`, `item_id`) | many-to-many, one level removed from `Restaurant` | Addons attach to **items**, not restaurants directly: `Restaurant → Item → AddonCategoryItem → AddonCategory → Addon`. |
| 3.5 | `MediaAsset` (cover + gallery images) | Polymorphic `owner_type`/`owner_id` (`ownerType = "RESTAURANT"` for gallery, `"RESTAURANT_COVER"` for the single cover image) | one-to-many | Generic table shared by every uploadable entity in the app, not restaurant-specific. Max 5 gallery images, enforced in `RestaurantService.uploadImage`. |
| 3.6 | `Location` (named service area — "Koramangala") | `Restaurant.locationId` (`String`) | **loose tag, no FK** | See §3.6 detail below — this is not a real relationship yet. |
| 3.7 | Owning `User` (store owner) | Join table `restaurant_user` (`RestaurantUser` entity: `user_id`, `restaurant_id`) | many-to-many | An owner can run several restaurants (`RestaurantService.assertOwnership`); admin-created restaurants have no row here at all. |
| 3.8 | `Coupon` | `Coupon.restaurantId` (nullable) | many-to-one, optional | `NULL` = platform-wide coupon (admin-created); set = one restaurant's own coupon (store-owner or admin created — `Coupon.createdBy` distinguishes who). |
| 3.9 | `RestaurantAuditLog` | `restaurant_id` on the log row | one-to-many | Field-level "what changed, from what, to what, by whom" trail, written by `RestaurantService.applyField()`/`applyCategoryIds()` on every admin patch. Lives in `pureeats-catalog-service`'s own `entity` package, not `domain` — unlike everything else in this table, it's local to catalog-service since nothing else needs to read it. |
| 3.10 | `DeliveryGuyRestaurant` | `restaurant_id` | many-to-many (with `DeliveryGuyDetail`) | Admin-managed allowlist restricting which restaurants a rider may pick up from — unrelated to opening hours or delivery radius. |
| 3.11 | `RestaurantEarning` / `RestaurantPayout` | `restaurant_id` | one-to-many | Financial ledger, out of scope for this doc beyond noting it exists. |

### 3.6 in detail — "Serviceable location" is not a real relationship (yet)

`Restaurant.locationId` is a plain `String` column with no `@JoinColumn`, no FK constraint, and no
query anywhere in the backend that filters or joins on it (`grep -r getLocationId` turns up exactly
two hits: the admin patch handler and the detail-response mapper — both just pass the value through).
`Location` itself (`locations` table: `name`, `description`, `is_popular`, `is_active`) is a
completely separate, unrelated concept from delivery-radius matching — that's computed straight from
`Restaurant.latitude`/`longitude` + `deliveryRadius` (`RestaurantService.findNearby`,
`checkDeliveryArea`), independent of `locationId` entirely.

Concretely, today:
- **Admin panel**: picks a location by id from a dropdown (`GET /locations`, and separately a full
  CRUD screen at `/admin/locations` → `AdminLocationController`), stores the id on the restaurant.
- **Customer app**: never reads `locationId` at all (`grep -r locationId` in
  `pureeats-customer-react-app-2026/src` returns nothing).
- **API surface**: `RestaurantSummaryResponse` (used by every listing/card screen — Home, Search,
  category listing, Top Picks) doesn't include `locationId` at all. Only
  `RestaurantDetailResponse` (single-restaurant detail) does, and only as the bare id — never
  resolved to the location's `name`. A client would have to separately call `GET /locations` and
  cross-reference itself; nothing does.

So it's currently a write-only admin-panel field with no read-side consumer and no relational
integrity — a label, not a filter.

## 4. How opening hours are stored — two representations, one is legacy

| Column | Shape | Written by | Read by |
|---|---|---|---|
| `opening_time` / `closing_time` | Two `LocalTime` columns — one daily window | `RestaurantService.buildRestaurant`/`patchAsAdmin`, and indirectly by the admin form's schedule editor (see below) | `RestaurantAvailabilityRule` (checkout gate) and the customer app's `getOpenStatus()` — **the only two places that decide "open now"** |
| `schedule_data` | One `String` column, holding a JSON array of 7 `{day, isOpen, slots:[{open,close}]}` objects | `RestaurantScheduleCodec.validateAndSerialize()`, called from `buildRestaurant`/`patchAsAdmin` when the admin form submits `weeklySchedule` | `RestaurantScheduleCodec.deserialize()`, feeding `RestaurantDetailResponse.weeklySchedule` back to the admin edit form only |

`schedule_data` reuses a column that pre-existed and was completely unused before the weekly-hours
feature was built — no migration was needed, just a codec (`RestaurantScheduleCodec`) that
validates (known day names, no duplicate days, an open day needs ≥1 slot, slots can't overlap or
duplicate, close must be after open) and serializes/deserializes the JSON.

**The two columns are not kept in sync by the backend.** The link is entirely client-side: the admin
panel's `WeeklyScheduleEditor` → `RestaurantForm.tsx` derives `openingTime`/`closingTime` from the
weekly schedule on every edit —

```ts
const firstOpenDay = schedule.find((d) => d.isOpen && d.slots.length > 0)
onChange('openingTime', firstOpenDay.slots[0].open)
onChange('closingTime', firstOpenDay.slots[firstOpenDay.slots.length - 1].close)
```

— i.e. "first open day found, its first slot's start, its last slot's end." This is a lossy
approximation: it collapses a split lunch/dinner schedule (e.g. 09:00–14:00 + 18:00–22:00) into one
continuous 09:00–22:00 window, and it isn't day-aware — Monday's hours might get used as the
legacy window even though today is Thursday.

## 5. How "open now / closed" is actually decided — the gap

Two independent implementations, both bypassing `schedule_data` entirely:

**Backend — checkout gate** (`pureeats-order-service/.../cartvalidation/RestaurantAvailabilityRule.java`):
```java
if (restaurant.getOpeningTime() != null && restaurant.getClosingTime() != null && !isWithinOpeningHours(restaurant)) {
    return List.of(CartIssue.restaurantLevel("This restaurant is closed right now (opens " + restaurant.getOpeningTime() + ")"));
}
```
Runs on every cart validation and again inside `OrderService.placeOrder()` (same rule list, so the
two can never disagree with *each other* — see `ORDER_JOURNEY_ARCHITECTURE.html` — but both still
only look at the single window).

**Customer app — card/menu greying** (`pureeats-customer-react-app-2026/src/lib/restaurantAvailability.ts`
→ `restaurantHours.ts`):
```ts
export function isRestaurantOrderable(restaurant): boolean {
  if (!restaurant.isActive || !restaurant.isAccepted) return false
  return getOpenStatus(restaurant.openingTime, restaurant.closingTime).isOpen
}
```
A second, independent reimplementation of the same single-window logic (it even re-solves the
overnight-wraparound case, e.g. 18:00–02:00, separately from the backend's version) — reading a
`Pick<Restaurant, 'openingTime' | 'closingTime'>` that doesn't even request `weeklySchedule`.

**Net effect**: a restaurant configured with, say, Mon–Fri 09:00–14:00 + 18:00–22:00 and closed
weekends will, in both the checkout gate and the customer app, actually be evaluated as "open
09:00–22:00 every day of the week including weekends" — whatever its derived legacy window happens
to be — regardless of the real per-day schedule sitting in `schedule_data`.

### If this gets fixed later

Not attempted here — this doc is descriptive, not a change — but the shape of a real fix would be:
compute "open now" from `schedule_data` (today's weekday + current time against that day's slots) in
**one place**, most naturally a backend-computed `isOpenNow`/`opensAt`/`closesAt` field added to
`RestaurantSummaryResponse`/`RestaurantDetailResponse`, so neither the order-service cart rule nor
the customer app has to re-derive or duplicate the logic — both would just read the field the backend
already decided. The overnight-window handling already written twice (Java + TypeScript) is exactly
the kind of logic that duplication like this tends to let drift.

## 6. Main classes, by responsibility

**Entities** (`domain/entity`, unless noted): `Restaurant`, `RestaurantCategory`, `Location`,
`PopularGeoPlace`, `Item`, `ItemCategory`, `Addon`, `AddonCategory`, `AddonCategoryItem`, `Coupon`,
`RestaurantUser`, `DeliveryGuyRestaurant`, `RestaurantEarning`, `RestaurantPayout`; plus
`RestaurantCategoryRestaurant` and `RestaurantAuditLog` (`pureeats-catalog-service/.../entity`, local
to that module — see §3.9).

**DTOs** (`pureeats-catalog-service/.../dto`): `RestaurantCreateRequest`, `RestaurantPatchRequest`
(admin — every field optional, diffed and audit-logged), `RestaurantUpdateRequest` (store-owner
self-edit — deliberately excludes admin-only fields like `commissionRate`), `RestaurantDetailResponse`,
`RestaurantSummaryResponse`, `DayScheduleDto`/`TimeSlotDto` (the weekly-schedule wire shape),
`LocationRequest`/`LocationAdminResponse`/`LocationResponse`.

**Services**: `RestaurantService` (the hub — create/patch/query, delegates schedule work to
`RestaurantScheduleCodec` and category-link work to its own `replaceCategoryLinks`/`applyCategoryIds`),
`RestaurantScheduleCodec` (validate + JSON (de)serialize `schedule_data`), `RestaurantCategoryService`
(category CRUD + `restaurantsInCategory`), `LocationService`, `RestaurantAuditLogService`, `MenuService`
(items/categories), `MediaAssetService`.

**Controllers**: `AdminRestaurantController` (`/api/v1/admin/restaurants/**`, ADMIN/SUPER_ADMIN only),
`RestaurantController` (`/api/v1/restaurants/**`, public — customer-facing discovery/detail/menu),
`StoreOwnerRestaurantController` (`/api/v1/store-owner/restaurants/**`), `RestaurantCategoryController`
(public), `AdminLocationController`, `LocationController` (public).

**Cart validation**: `RestaurantAvailabilityRule` — one of several `CartValidationRule` components
`CartValidationService` fans out to; the one relevant here (see §5).

---

See [`RESTAURANT_DOMAIN_ARCHITECTURE.html`](RESTAURANT_DOMAIN_ARCHITECTURE.html) for the same
material with an architecture diagram, an entity-relationship diagram, a class diagram, and a
sequence diagram of the open/closed decision gap.
