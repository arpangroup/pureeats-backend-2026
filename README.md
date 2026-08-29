# PureEats — Spring Boot Backend

A multi-module Spring Boot rewrite of the PureEats food-delivery API (originally Laravel). Pure REST API, no server-rendered UI — the [React frontend](../pureeats-react-ui) is the client. Secured with **Spring Security + JWT**, documented with **springdoc-openapi / Swagger UI**, backed by **MySQL**.

---

## Table of contents

- [Tech stack](#tech-stack)
- [Module structure](#module-structure)
- [Getting started](#getting-started)
- [Explore the API](#explore-the-api)
- [Security & JWT](#security--jwt)
- [Entity relationships](#entity-relationships)
- [API reference](#api-reference)
- [Configuration reference](#configuration-reference)
- [Known limitations / not yet built](#known-limitations--not-yet-built)

---



## Tech stack

| Concern | Choice |
|---|---|
| Language / runtime | Java 21 |
| Framework | Spring Boot 4.1.1 / Spring Framework 7 |
| Build | Maven (multi-module reactor) |
| Persistence | Spring Data JPA + Hibernate 7, MySQL/MariaDB |
| Security | Spring Security 7, stateless JWT (`jjwt`) |
| API docs | springdoc-openapi (OpenAPI 3 / Swagger UI) |
| Object mapping | Jackson 3 |
| Boilerplate | Lombok |

---

## Module structure

```
pureeats-parent (pom)
├── domain                          entities, enums, exceptions, response envelope — no web/JPA-impl deps
├── pureeats-user-service           auth, JWT issuance/validation, users, addresses, rider onboarding, roles
├── pureeats-catalog-service        restaurants, menu, addons, coupons, discovery content
├── pureeats-notification-service   push tokens, in-app alerts, NotificationDispatchService
├── pureeats-order-service          orders, delivery/rider workflow, wallet, earnings, support tickets
├── pureeats-rating-service         restaurant & driver ratings
└── pureeats-app                    the ONLY runnable module — security config, OpenAPI, application.yml
```

**Dependency graph** (strictly acyclic — each arrow is "depends on"):

```mermaid
graph LR
    domain --> user[pureeats-user-service]
    domain --> catalog[pureeats-catalog-service]
    domain --> notif[pureeats-notification-service]
    user --> catalog
    user --> order[pureeats-order-service]
    catalog --> order
    notif --> order
    user --> rating[pureeats-rating-service]
    order --> rating
    user --> app[pureeats-app]
    catalog --> app
    notif --> app
    order --> app
    rating --> app
```

**Why this split:**
- `domain` is the shared kernel: every module depends on it, it depends on nothing (no Spring Web/Data — just `jakarta.persistence-api` + Lombok). Holds JPA entities (flat, FK-as-`Long`/`Integer` columns — **no `@ManyToOne`/`@OneToMany`**, deliberately, to avoid cross-module lazy-loading and keep entities framework-light), shared enums (`Role`, `OrderStatusCode`, `DiscountType`, `PaymentMode`, `DeliveryType`, `CommissionBasis`), the `ApiException`/`ApiResponse` envelope, and `CurrentUserContext` (a `ThreadLocal<Long>` set by the JWT filter).
- `pureeats-user-service` owns identity: issuing/validating JWTs, password auth, OTP login, roles (mirrors the legacy Spatie `roles`/`model_has_roles` schema so seeded data keeps working), addresses, rider (`DeliveryGuyDetail`) profiles.
- `pureeats-catalog-service` depends on `user-service` only to call `RoleService.assignRole(...)` when a user registers their first restaurant (grants `RESTAURANT_OWNER`).
- `pureeats-order-service` is the busiest module — it depends on `user-service` (addresses, rider limits), `catalog-service` (menu pricing, coupon validation), and `notification-service` (order-event pushes).
- `pureeats-notification-service` and `pureeats-rating-service` are kept minimal/independent so nothing else is forced to depend on them.
- **Authorization is centralized**, not scattered: `pureeats-app`'s single `SecurityFilterChain` gates URL prefixes by role (`/api/v1/store-owner/**` → `RESTAURANT_OWNER`, `/api/v1/delivery/**` → `DELIVERY`, `/api/v1/admin/**` → `ADMIN`). Business modules never need a Spring Security dependency for that — they read `CurrentUserContext.get()` (plain `Long`, no framework needed) or `@AuthenticationPrincipal AuthenticatedUser` for row-level ownership checks (e.g. "does this restaurant belong to this owner").

---

## Explore the API
- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- Health check: `http://localhost:8080/actuator/health`

Click **Authorize** in Swagger UI and paste a JWT (obtained from `POST /api/v1/auth/register` or `/login`) to call protected endpoints.

---

## Getting started

### Prerequisites
- JDK 21, Maven 3.9+, a MySQL/MariaDB instance.

### Build
```bash
mvn -DskipTests clean package
```

### Run
```bash
java -jar pureeats-app/target/pureeats-app-0.0.1-SNAPSHOT.jar
```
Configure via environment variables (see [Configuration reference](#configuration-reference)) — at minimum `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`. On first run, `spring.jpa.hibernate.ddl-auto=update` creates the schema automatically.

### Run from IntelliJ IDEA

Every config value in `application.yml` already has a `${VAR:default}` fallback, so the app boots with zero configuration — but for a real dev run you want your own `DB_NAME`/`JWT_SECRET` rather than the shared placeholders. Set up a Run/Debug Configuration:

1. **Run → Edit Configurations… → + → Application**
2. Main class: `com.pureeats.app.PureEatsApplication`
3. Use classpath of module: `pureeats-app`
4. Paste the following into **VM options** (click "Modify options" → "Add VM options" first if the field isn't shown):

```
-DDB_HOST=localhost -DDB_PORT=3306 -DDB_NAME=pureeats_dev -DDB_USERNAME=root -DDB_PASSWORD= -DJPA_DDL_AUTO=update -DSERVER_PORT=8080 -DJWT_SECRET=local-intellij-dev-secret-key-change-me-min-32-bytes-long -DJWT_EXPIRATION_MS=86400000 -DOTP_DEV_MODE=true -DTAX_PERCENTAGE=5 -DCOMMISSION_BASIS=FULL_ORDER
```

5. Make sure MySQL/MariaDB is running and a database named `pureeats_dev` exists (or change `DB_NAME` above — `ddl-auto=update` creates the tables on first run).

**Alternative**: paste the same values into IntelliJ's separate **Environment variables** field instead (same effect, no `-D`/dashes needed) — use one field or the other, not both:

```
DB_HOST=localhost;DB_PORT=3306;DB_NAME=pureeats_dev;DB_USERNAME=root;DB_PASSWORD=;JPA_DDL_AUTO=update;SERVER_PORT=8080;JWT_SECRET=local-intellij-dev-secret-key-change-me-min-32-bytes-long;JWT_EXPIRATION_MS=86400000;OTP_DEV_MODE=true;TAX_PERCENTAGE=5;COMMISSION_BASIS=FULL_ORDER
```

---

## Security & JWT

- **Login/register** (`pureeats-user-service`) issue an HS256 JWT containing: `sub` (userId), `name`, `email`, `phone`, `role`, and `deliveryGuyDetailId` (riders only) — enough for the frontend or any internal caller to know "who is this and what can they do" without another API call.
- **Roles**: `ADMIN`, `RESTAURANT_OWNER`, `DELIVERY`, `CUSTOMER`, `EMPLOYEE`. Every account starts as `CUSTOMER`; registering a restaurant grants `RESTAURANT_OWNER`, registering a rider profile grants `DELIVERY`. A user can hold multiple roles simultaneously (mirrors the legacy schema) — the JWT carries the single highest-priority one (`ADMIN` > `EMPLOYEE` > `RESTAURANT_OWNER` > `DELIVERY` > `CUSTOMER`).
- **Role changes require re-login** — the JWT is stateless and not re-issued mid-session (e.g. after `POST /api/v1/store-owner/restaurants`, log in again to get a `RESTAURANT_OWNER`-role token).
- **Onboarding exception**: `POST /api/v1/store-owner/restaurants` is reachable by *any* authenticated user (not just existing owners), since that's the endpoint that grants the role in the first place — every other `/api/v1/store-owner/**` route requires the role already.
- Stateless sessions, CSRF disabled (no cookies involved), permissive CORS (tighten `SecurityConfig.corsConfigurationSource()` for production).

---

## Entity relationships

Entities are **flat JPA `@Entity` classes with plain FK-id columns** (`Long`/`Integer`), not `@ManyToOne`/`@OneToMany` associations — this is intentional (see [Module structure](#module-structure)). The diagrams below show the *logical* relationships (matching ID columns), grouped by owning module.

### Identity & users (`pureeats-user-service`)

```mermaid
erDiagram
    USER ||--o{ ADDRESS : "user_id"
    USER ||--o| DELIVERY_GUY_DETAIL : "delivery_guy_detail_id"
    USER ||--o{ LOGIN_SESSION : "user_id"
    USER ||--o{ PASSWORD_RESET_OTP : "user_id"
    USER ||--o{ MODEL_HAS_ROLE : "model_id"
    ROLE ||--o{ MODEL_HAS_ROLE : "role_id"
    ROLE ||--o{ ROLE_HAS_PERMISSION : "role_id"
    PERMISSION ||--o{ ROLE_HAS_PERMISSION : "permission_id"
    PERMISSION ||--o{ MODEL_HAS_PERMISSION : "permission_id"

    USER {
        Long id PK
        string name
        string email UK
        string phone UK
        string password
        string isActive
        Integer defaultAddressId FK
        Integer deliveryGuyDetailId FK
    }
    ADDRESS {
        Long id PK
        Integer userId FK
        string house
        string address
        string latitude
        string longitude
    }
    DELIVERY_GUY_DETAIL {
        Long id PK
        string vehicleNumber
        BigDecimal commissionRate
        Integer maxAcceptDeliveryLimit
        BigDecimal rating
    }
    ROLE {
        Long id PK
        string name
    }
    PERMISSION {
        Long id PK
        string name
    }
```

`ModelHasRole.modelType`/`ModelHasPermission.modelType` store the legacy morph class name `App\User`. `SmsOtp` (phone → OTP, login flow) has no FK — it's looked up by phone number directly.

### Catalog (`pureeats-catalog-service`)

```mermaid
erDiagram
    RESTAURANT ||--o{ RESTAURANT_USER : "restaurant_id"
    RESTAURANT ||--o{ ITEM : "restaurant_id"
    RESTAURANT ||--o{ COUPON : "restaurant_id (0 = global)"
    RESTAURANT ||--o{ RESTAURANT_CATEGORY_RESTAURANT : "restaurant_id"
    RESTAURANT_CATEGORY ||--o{ RESTAURANT_CATEGORY_RESTAURANT : "restaurant_category_id"
    ITEM_CATEGORY ||--o{ ITEM : "item_category_id"
    ITEM ||--o{ ADDON_CATEGORY_ITEM : "item_id"
    ADDON_CATEGORY ||--o{ ADDON_CATEGORY_ITEM : "addon_category_id"
    ADDON_CATEGORY ||--o{ ADDON : "addon_category_id"
    COUPON ||--o{ COUPON_USAGE : "coupon_id"

    RESTAURANT {
        Long id PK
        string name
        string slug UK
        string sku UK
        BigDecimal deliveryCharges
        BigDecimal deliveryRadius
        BigDecimal commissionRate
        boolean isActive
        boolean isAccepted
        boolean autoAcceptable
    }
    RESTAURANT_USER {
        Long id PK
        Long userId FK
        Long restaurantId FK
    }
    ITEM {
        Long id PK
        Integer restaurantId FK
        Integer itemCategoryId FK
        BigDecimal price
        boolean isActive
    }
    COUPON {
        Long id PK
        string code UK
        string discountType
        string discount
        Integer restaurantId FK
    }
```

`Location`, `PopularGeoPlace`, `PromoSlider` → `Slide`, `Page`, `Setting`, `PaymentGateway`, `SmsGateway`, `Translation` are standalone reference/content tables (no FKs into the order/catalog graph).

### Orders & delivery (`pureeats-order-service`)

```mermaid
erDiagram
    ORDER ||--o{ ORDER_ITEM : "order_id"
    ORDER_ITEM ||--o{ ORDER_ITEM_ADDON : "orderitem_id"
    ORDER ||--o| ACCEPT_DELIVERY : "order_id"
    ORDER ||--o{ GPS_TABLE : "order_id"
    ORDER ||--o| TRIP_DETAIL : "order_id"
    ORDER_STATUS ||--o{ ORDER : "orderstatus_id"
    RESTAURANT_EARNING ||--o| RESTAURANT_PAYOUT : "restaurant_payout_id"
    DELIVERY_COLLECTION ||--o{ DELIVERY_COLLECTION_LOG : "delivery_collection_id"
    WALLET ||--o{ TRANSACTION : "wallet_id"

    ORDER {
        Long id PK
        string uniqueOrderId UK
        Integer orderstatusId FK
        Integer userId FK
        Integer restaurantId FK
        string deliveryPin
        BigDecimal total
        BigDecimal payable
        string paymentMode
    }
    ORDER_ITEM {
        Long id PK
        Integer orderId FK
        Integer itemId FK
        int quantity
        BigDecimal price
    }
    ACCEPT_DELIVERY {
        Long id PK
        Integer orderId FK
        Integer userId FK
        boolean isComplete
    }
    WALLET {
        Long id PK
        string holderType
        Long holderId
        Long balance
    }
    TRANSACTION {
        Long id PK
        Long walletId FK
        string type
        Long amount
    }
```

`AcceptDelivery.userId` is the assigned rider's user id. `Wallet.holderType`/`holderId` is a polymorphic FK (only `App\User` holders are used — see `WalletService`); `Wallet.balance`/`Transaction.amount` are integer minor units (paise). `OrderStatusCode` (enum in `domain`) is seeded into `order_statuses` on startup by `OrderStatusSeeder` — `Order.orderstatusId` is always resolved through `OrderStatusService`, never a hardcoded number.

### Ratings & notifications

```mermaid
erDiagram
    ORDER ||--o{ RATING : "order_id"
    RESTAURANT ||--o{ RATING : "rateable_id"
    DELIVERY_GUY_DETAIL ||--o{ RATING : "rateable_id"
    USER ||--o{ ALERT : "user_id"
    USER ||--o{ PUSH_TOKEN : "user_id"

    RATING {
        Long id PK
        Integer orderId FK
        string rateableType
        Long rateableId
        int rating
    }
    ALERT {
        Long id PK
        Long userId FK
        string data
        boolean isRead
    }
    PUSH_TOKEN {
        Long id PK
        Integer userId FK
        string token
        boolean isActive
    }
```

`Rating.rateableType`/`rateableId` is a polymorphic reference (legacy Laravel morph pattern) — `App\Restaurant` or `App\DeliveryGuyDetail`, resolved via the `RateableType` enum in `pureeats-rating-service`.

---

## API reference

Base path: `/api/v1`. All endpoints return the envelope `{ success, message, data, timestamp }` (see `ApiResponse`). 🔒 = requires `Authorization: Bearer <token>`; role noted where narrower than "any authenticated user".

### Auth — `AuthController`, `PasswordResetController`
| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/auth/register` | public | Register a new customer account |
| POST | `/auth/login` | public | Login with email/phone + password |
| POST | `/auth/otp/send` | public | Send a login OTP to a phone number |
| POST | `/auth/otp/login` | public | Login (or auto-register) via verified OTP |
| POST | `/auth/password/forgot` | public | Send a password-reset code by email |
| POST | `/auth/password/verify` | public | Verify a password-reset code |
| POST | `/auth/password/reset` | public | Set a new password using a verified code |

### User profile & addresses — `UserController`, `AddressController`, `RiderController`
| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/users/me` | 🔒 | Get own profile |
| PUT | `/users/me` | 🔒 | Update name/photo |
| GET | `/users/me/addresses` | 🔒 | List saved addresses |
| POST | `/users/me/addresses` | 🔒 | Save a new address |
| PUT | `/users/me/addresses/{id}` | 🔒 | Edit an address |
| DELETE | `/users/me/addresses/{id}` | 🔒 | Delete an address |
| PATCH | `/users/me/addresses/{id}/default` | 🔒 | Set as default address |
| POST | `/users/me/rider-profile` | 🔒 | Register as a delivery rider (grants `DELIVERY`) |
| GET | `/users/me/rider-profile` | 🔒 | Get own rider profile |

### Restaurants & menu — `RestaurantController`, `StoreOwnerRestaurantController`, `StoreOwnerMenuController`, `StoreOwnerAddonController`
| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/restaurants` | public | List active, accepted restaurants |
| GET | `/restaurants/search?q=` | public | Search restaurants by name |
| GET | `/restaurants/{id}` | public | Restaurant detail by id |
| GET | `/restaurants/slug/{slug}` | public | Restaurant detail by slug |
| GET | `/restaurants/{id}/items` | public | Active menu items |
| POST | `/restaurants/{id}/check-delivery-area` | public | Haversine check: is a lat/long in delivery radius |
| GET | `/store-owner/restaurants` | 🔒 owner | List restaurants you own |
| POST | `/store-owner/restaurants` | 🔒 any | Onboard a new restaurant (grants `RESTAURANT_OWNER`) |
| PUT | `/store-owner/restaurants/{id}` | 🔒 owner | Update a restaurant you own |
| PATCH | `/store-owner/restaurants/{id}/enable` \| `/disable` | 🔒 owner | Toggle a restaurant |
| GET / POST | `/store-owner/item-categories` | 🔒 owner | List / create item categories |
| PATCH | `/store-owner/item-categories/{id}/enable` \| `/disable` | 🔒 owner | Toggle a category |
| POST | `/store-owner/restaurants/{restaurantId}/items` | 🔒 owner | Add a menu item |
| PUT | `/store-owner/items/{id}` | 🔒 owner | Update a menu item |
| PATCH | `/store-owner/items/{id}/enable` \| `/disable` | 🔒 owner | Toggle a menu item |
| GET / POST | `/store-owner/addon-categories` | 🔒 owner | List / create addon categories |
| GET | `/store-owner/addon-categories/{id}/addons` | 🔒 owner | List addons in a category |
| POST | `/store-owner/addons` | 🔒 owner | Create an addon |
| PATCH | `/store-owner/addons/{id}/enable` \| `/disable` | 🔒 owner | Toggle an addon |

### Coupons — `CouponController`
| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/coupons?restaurantId=` | public | List coupons available for a restaurant (incl. global) |
| POST | `/coupons/preview` | 🔒 | Validate a code and preview the discount |
| POST | `/store-owner/coupons` | 🔒 owner | Create a coupon |

### Discovery content — `RestaurantCategoryController`, `LocationController`, `ContentController`
| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/restaurant-categories` | public | List cuisine/category tags |
| GET | `/restaurant-categories/{id}/restaurants` | public | Restaurants in a category |
| GET | `/locations/search?q=` \| `/popular` \| `/popular-geo-places` | public | Location lookups |
| GET | `/pages` \| `/pages/{slug}` | public | CMS pages |
| GET | `/settings` | public | Public app-settings blob |
| GET | `/promo-sliders` | public | Promo sliders with slides |
| GET | `/languages` | public | Available languages |
| GET | `/payment-gateways` | public | Active payment gateways |

### Orders & delivery — `OrderController`, `StoreOwnerOrderController`, `StoreOwnerEarningsController`, `DeliveryOrderController`, `WalletController`, `SupportController`
| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/orders` | 🔒 | Place an order |
| GET | `/orders` | 🔒 | List own orders |
| GET | `/orders/{id}` | 🔒 | Order detail |
| PATCH | `/orders/{id}/cancel` | 🔒 | Cancel an order |
| GET | `/store-owner/restaurants/{rid}/orders/new` \| `/running` | 🔒 owner | Order queues |
| POST | `.../orders/{id}/accept` \| `/ready` \| `/self-pickup-complete` \| `/cancel` | 🔒 owner | Order workflow |
| GET | `/store-owner/restaurants/{rid}/earnings` | 🔒 owner | Unsettled earnings |
| POST | `/store-owner/restaurants/{rid}/earnings/payout-request` | 🔒 owner | Request payout |
| GET | `/delivery/orders/available` | 🔒 rider | Orders available to deliver |
| POST | `/delivery/orders/{id}/accept` \| `/pickup` \| `/deliver` | 🔒 rider | Delivery workflow (deliver requires the customer's PIN) |
| POST | `/delivery/gps` | 🔒 rider | Report current GPS location |
| GET | `/delivery/orders/{id}/gps` | 🔒 | Last known rider location |
| GET | `/users/me/wallet` \| `/wallet/transactions` | 🔒 | Wallet balance / ledger |
| POST | `/support` | 🔒 | Raise a support ticket |
| GET | `/support` | 🔒 | List own tickets |

### Ratings & notifications — `RatingController`, `NotificationController`
| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/ratings/ratable-orders` | 🔒 | Delivered orders still unrated |
| POST | `/ratings` | 🔒 | Submit a restaurant or driver rating |
| GET | `/ratings/restaurants/{id}` \| `/average` | public | Restaurant ratings |
| GET | `/ratings/drivers/{id}` \| `/average` | public | Driver ratings |
| POST | `/notifications/push-token` | 🔒 | Register/refresh a push token |
| GET | `/notifications` | 🔒 | Recent notifications (7 days, max 20) |
| PATCH | `/notifications/read-all` \| `/{id}/read` | 🔒 | Mark notifications read |

---

## Configuration reference

All in `pureeats-app/src/main/resources/application.yml`, overridable via environment variable:

| Env var | Default | Purpose |
|---|---|---|
| `DB_HOST` | `localhost` | MySQL host |
| `DB_PORT` | `3306` | MySQL port |
| `DB_NAME` | `pureeats` | Database name |
| `DB_USERNAME` | `root` | DB user |
| `DB_PASSWORD` | *(empty)* | DB password |
| `JPA_DDL_AUTO` | `update` | Hibernate schema strategy (`validate`/`none` for production) |
| `SERVER_PORT` | `8080` | HTTP port |
| `JWT_SECRET` | dev placeholder | **Must override in production** — HS256 key, 32+ bytes |
| `JWT_EXPIRATION_MS` | `86400000` (24h) | JWT lifetime |
| `OTP_DEV_MODE` | `true` | When true, generated OTP/reset codes are returned in the API response (no SMS/email gateway wired up yet) — **set false once one is** |
| `TAX_PERCENTAGE` | `5` | Flat tax % applied at order placement |
| `COMMISSION_BASIS` | `FULL_ORDER` | `FULL_ORDER` or `DELIVERY_CHARGE_ONLY` — what a rider's commission % is applied against |

---

## Known limitations / not yet built

Flagged deliberately (need external credentials or product decisions only you can make):

- **Admin panel/APIs** — no `/api/v1/admin/**` controllers yet (the URL prefix is reserved and role-gated in `SecurityConfig`, ready to receive them).
- **Real payment gateways** — Razorpay/Paytm/PayUmoney/MercadoPago are not integrated; `/payment-gateways` only lists configured rows, COD/WALLET are the only payment modes actually processed.
- **SMS/email delivery** — OTP and password-reset codes are generated and stored but not sent; returned directly in the response while `OTP_DEV_MODE=true`.
- **Bulk upload, geocoder integration, `Translation`/`SmsGateway` admin CRUD** — entities exist in `domain`, no service/controller layer yet.
- **Delivery-charge tiering** — uses a flat `Restaurant.deliveryCharges`, not the legacy distance-tiered calculation.
