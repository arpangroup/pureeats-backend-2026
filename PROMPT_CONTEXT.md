# PureEats Backend — Context Primer

> Paste this file's content as context at the start of a new session working on this repo. It's written to be dense and reference-able, not to read nicely — see `README.md` for the human-friendly version.

## What this is

Spring Boot rewrite of the Laravel PureEats food-delivery API (`C:\xampp\htdocs\pureeats`). Pure REST backend — the React app in the sibling repo `pureeats-react-ui` is the only client. Repo root: `D:\workspace\pureeats-backend-2026`, git branch `feature/26.08.28-initial-dev-1`. Package root: `com.pureeats`. MySQL/MariaDB. Java 21.

## Environment quirk — READ THIS FIRST

This machine's local Maven repo has **Spring Boot 4.1.1 / Spring Framework 7 / Jackson 3** — ahead of what most training data calls "current". Do not assume Spring Boot 3.x class/package locations here:
- Starters are split/renamed: `spring-boot-starter-webmvc` (not `-web`), `spring-boot-starter-webmvc-test` (not `-test`). Autoconfigure is many small `spring-boot-<feature>` jars, not one `spring-boot-autoconfigure`.
- `@EntityScan` → `org.springframework.boot.persistence.autoconfigure.EntityScan`.
- `UserDetailsServiceAutoConfiguration` → `org.springframework.boot.security.autoconfigure` (excluded in `PureEatsApplication` since auth is JWT-only).
- Jackson 3 package is `tools.jackson.databind` (not `com.fasterxml.jackson`). A `JsonMapperBuilderCustomizer` bean in `pureeats-app`'s `JacksonConfig` disables `DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES` so record DTOs with primitive `boolean`/`int` fields don't blow up when a client omits that field.
- MySQL/MariaDB reserved words `key` and `desc` needed backtick-quoted `@Column(name = "`key`")` on `Setting.key` and `Item.desc` — check new column names against the reserved-word list before naming.
- **If the Write tool ever produces a garbled/UTF-16 file when overwriting a pre-existing file on this machine, delete the file first and rewrite as new** — this happened once with this repo's own `README.md` (was UTF-16 before being touched; overwriting preserved that encoding). Fresh files write correctly as UTF-8.

## Module graph (strictly acyclic, no cycles)

```
domain  (no deps: jakarta.persistence-api + Lombok only)
  → pureeats-user-service        (auth/JWT, users, addresses, riders, roles)
      → pureeats-catalog-service     (restaurants, menu, coupons, content — depends on user-service ONLY for RoleService.assignRole on restaurant onboarding)
          → pureeats-notification-service  (push tokens, alerts — independent, only depends on domain)
              → pureeats-order-service   (depends on user+catalog+notification: orders, delivery workflow, wallet, earnings, support)
                  → pureeats-rating-service  (depends on user+order: restaurant/driver ratings)
                      → pureeats-app         (THE ONLY runnable module: security, OpenAPI, exception handling, application.yml)
```

Package-per-module: `com.pureeats.{user|catalog|notification|order|rating|app}.{controller|service|repository|dto}`. Domain entities live in `com.pureeats.domain.entity`, shared enums in `com.pureeats.domain.enums`, shared exceptions in `com.pureeats.domain.common.exception`, the response envelope in `com.pureeats.domain.common.response.ApiResponse`.

**Golden rule for adding features**: figure out which module a feature's data belongs to using the graph above; a module may only import from modules strictly below it. If a new feature needs data from two "sibling" modules, put it in whichever module already depends on both (usually `order-service`), or add a one-directional dependency edge — never create a cycle.

## Entities (all in `domain`, flat `@Entity` classes — NO `@ManyToOne`/`@OneToMany`, just plain FK-id columns as `Long`/`Integer`)

Relationships are logical (matching ID columns), not JPA associations — this is deliberate, keeps modules decoupled on a shared DB. Full list + diagrams: `README.md` → Entity relationships. Groups:
- **Identity**: `User`, `Address`, `DeliveryGuyDetail`, `LoginSession`, `PasswordReset(Otp)`, `SmsOtp`, `Role`, `Permission`, `ModelHasRole`, `ModelHasPermission`, `RoleHasPermission` (legacy Spatie-shaped RBAC mirror, morph type `App\User`).
- **Catalog**: `Restaurant`, `RestaurantUser` (M:N ownership), `RestaurantCategory(Restaurant/Slider)`, `ItemCategory`, `Item`, `AddonCategory`, `Addon`, `AddonCategoryItem`, `Coupon`, `CouponUsage`, `Location`, `PopularGeoPlace`, `PromoSlider`, `Slide`, `Page`, `Setting`, `PaymentGateway`, `SmsGateway`, `Translation`.
- **Orders**: `Order`, `OrderItem`, `OrderItemAddon`, `OrderStatus` (lookup table, seeded from `OrderStatusCode` enum by `OrderStatusSeeder` on boot — never hardcode a status id), `AcceptDelivery` (order↔rider assignment), `GpsTable`, `TripDetail`, `RestaurantEarning`, `RestaurantPayout`, `Wallet` (polymorphic holder, `App\User` only in practice, balance = integer minor units/paise), `Transaction`, `Transfer` (unused — no service built on it), `Support`.
- **Ratings/notifications**: `Rating` (polymorphic `rateableType`/`rateableId`: `App\Restaurant` or `App\DeliveryGuyDetail`), `Alert`, `PushToken`.

## Security / JWT (`pureeats-user-service` issues, `pureeats-app` enforces)

- Roles enum (`domain.enums.Role`): `ADMIN`, `STORE_OWNER`, `DELIVERY`, `CUSTOMER`, `EMPLOYEE`. Every user starts `CUSTOMER`. A user can hold several roles at once (legacy schema allows it) — `RoleService.resolveRole` picks the highest-priority one for the JWT, priority `ADMIN > EMPLOYEE > STORE_OWNER > DELIVERY > CUSTOMER`.
- JWT claims: `sub`=userId, `name`, `email`, `phone`, `role`, `deliveryGuyDetailId` (riders only). HS256, `pureeats.jwt.secret`/`expiration-ms`.
- `JwtAuthenticationFilter` (in user-service) sets both the Spring Security `Authentication` (principal = `AuthenticatedUser` record) AND `CurrentUserContext` (plain `ThreadLocal<Long>` in `domain`) — business modules that don't want a Spring Security dependency read `CurrentUserContext.get()` instead of `@AuthenticationPrincipal`.
- **All role gating is centralized** in `pureeats-app`'s `SecurityConfig` (URL-pattern based: `/api/v1/store-owner/**`→`STORE_OWNER`, `/api/v1/delivery/**`→`DELIVERY`, `/api/v1/admin/**`→`ADMIN`, everything else just `authenticated()`). Business modules do row-level ownership checks themselves (e.g. `RestaurantService.assertOwnership`), not role checks.
- **Known sharp edge**: role-granting endpoints must be carved out of their own role's URL-gate BEFORE a user has that role. `POST /api/v1/store-owner/restaurants` (onboarding, grants `STORE_OWNER`) has an explicit `.authenticated()` matcher placed before the broader `hasRole("STORE_OWNER")` rule for this reason. Apply the same pattern to any future self-serve role upgrade.
- Role changes require re-login (stateless JWT, not re-issued mid-session).

## API surface

78 endpoints under `/api/v1/**`, uniform envelope `{success, message, data, timestamp}` (`ApiResponse`). Full table in `README.md`. Swagger UI at `/swagger-ui/index.html`, OpenAPI JSON at `/v3/api-docs`, bearer-auth scheme wired (`OpenApiConfig`). Tag-per-controller, resource-oriented paths (`/orders`, `/restaurants/{id}/items`, `/store-owner/restaurants/{id}/orders/{orderId}/accept`, etc.) — not Laravel's verb-in-path style.

## Business rules worth knowing before touching order/delivery code

- Pricing (`OrderPricingService`): `tax` = flat % (`pureeats.tax.percentage`, default 5) of amount-after-discount; `restaurantCharge` = `Restaurant.restaurantCharges`% of same; `deliveryCharge` = flat `Restaurant.deliveryCharges` (0 for self-pickup) — **not** the legacy distance-tiered calc (simplification, flagged in README).
- Coupons (`CouponService`): `PERCENTAGE`/`AMOUNT` discount types, capped by `uptoAmount`, scoped by `restaurantId` (`0`=global), usage-limited by `count`/`totalCoupon`. `preview()` validates without recording; `recordUsage()` (called from `OrderService.placeOrder`) validates again and atomically records — always call `recordUsage`, never trust a client-side `preview()` result at order-placement time.
- Rider commission (`DeliveryOrderService.deliver`): `DeliveryGuyDetail.commissionRate`% of either the full order total or just the delivery charge, controlled by `pureeats.commission.basis` (`CommissionBasis` enum, global setting — not per-restaurant like Laravel had it).
- Delivery PIN: generated at order placement (`Order.deliveryPin`, always non-null — required by schema), verified case-insensitively at `deliver()`. No bypass code (legacy had a hardcoded `'20200'` bypass — intentionally not carried over).
- Restaurant ownership is many-to-many (`RestaurantUser`): one owner can have several restaurants, one restaurant conceivably several owners. Always check via `RestaurantService.assertOwnership(userId, restaurantId)`, never assume 1:1.
- Wallet is User-only; restaurant settlement goes through `RestaurantEarning`→`RestaurantPayout` instead (`RestaurantPayoutService`), not `Wallet`.

## Deferred / not built (don't assume these exist)

Admin panel/APIs (`/api/v1/admin/**` is reserved+role-gated but empty), real payment gateways (Razorpay/Paytm/PayUmoney/MercadoPago — only COD/WALLET are actually processed), bulk upload, geocoder integration, `Translation`/`SmsGateway` admin CRUD, `Transfer` entity (no service uses it). The legacy `/auth/otp/*` + `PasswordResetController` codes are still generated+stored only, returned in-response when `pureeats.otp.dev-mode=true`, never sent.

## OTP-challenge auth subsystem (see AUTH_SECURITY.md for the full picture)

A second, newer auth flow lives alongside everything above: `POST /api/v1/auth/{signup,otp/initiate,otp/verify,otp/resend,refresh,logout,logout-all}`, orchestrated by `pureeats-user-service`'s `AuthenticationService` (thin — delegates to single-purpose collaborators: `OtpChallengeService`, `SessionService`, `DeviceService`, `LoginHistoryRecorder`, `BlocklistService`, `RateLimiter`, `IpGeolocationService`, `SecurityEventPublisher`, all under `com.pureeats.user.security.*`/`com.pureeats.user.otp`). `pureeats-notification-service` now exposes a real `NotificationService` (email via console/Gmail-SMTP, SMS via console-only-so-far) that `pureeats-user-service` depends on — new module edge, still acyclic (notification-service depends on nothing but `domain`). New tables: `otp_challenges`, `security_blocklist`, `user_devices`, `login_history`, `user_sessions`, `audit_logs`, `rate_limit_buckets`, `notification_logs`. `User` gained `phone_verified_at`/`account_status`/`locked_at`/`locked_until`/`lock_reason`, and `password` is now nullable (was already effectively nullable in practice for OTP-provisioned accounts - MySQL's non-strict mode just silently tolerated the NOT NULL violation before). Everything from before this section is completely untouched for backward compatibility - see AUTH_SECURITY.md for why the endpoint paths don't literally match the original OTP design brief's `/auth/login`/`/auth/verify`/`/auth/resend`.

## Local verification setup (if asked to run/test this)

XAMPP MariaDB is not a running Windows service here — start manually: `C:\xampp\mysql\bin\mysqld.exe --defaults-file="C:\xampp\mysql\bin\my.ini" --standalone` (background). Use an isolated DB for testing (e.g. `pureeats_springboot_verify`), never point `ddl-auto=update` at the live Laravel `pureeats` DB — schema conflicts. Build: `mvn -DskipTests clean package` from repo root. Run: `java -jar pureeats-app/target/pureeats-app-*.jar` with `DB_NAME`/`DB_USERNAME`/`DB_PASSWORD`/`JWT_SECRET` env vars set (see README's Configuration reference table for the full list).
