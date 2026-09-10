# Push Notifications

Architecture reference for Firebase Cloud Messaging (FCM) across the backend, the customer app,
and the admin panel. Read this before adding a new kind of push — the goal is that a new
notification is a small, obvious change (a few lines at the call site), never a redesign.

## The three shapes every push is

Everything sent through this system is one of exactly three shapes. Picking the right one is the
first decision to make, before writing any code.

| Shape | Who receives it | Pops up? | Mechanism |
|---|---|---|---|
| **Individual, visible** | One user (all their devices) | Yes — title/body, optionally image/click-link/action buttons | `PushDisplayMode.VISIBLE`, targeted by `userId` |
| **Individual, silent** | One user (all their devices) | Never | `PushDisplayMode.SILENT`, targeted by `userId` |
| **Broadcast** | Everyone subscribed to a topic | Depends on the send (topics can carry either display mode too) | targeted by `topic`, not `userId` |

Rule of thumb: if the user should be *interrupted* by it (an alert, a promo, "your order was
delivered") → **visible**. If it's just keeping their already-open UI in sync with reality (an
order status ticking forward while they're looking at the tracking page) → **silent**. If it's
going to more than one specific person → **topic**, not a loop over users.

## Backend architecture

```
notify(...)                          <- caller (OrderNotificationService, or a controller)
  -> NotificationService              generic per-channel dispatch (EMAIL/SMS/PUSH/IN_APP/...)
      -> PushNotificationSender       the PUSH channel specifically - resolves userId -> device tokens
          -> FcmSender.send(FcmPushRequest)   the only class that talks to Firebase
```

### `PushDisplayMode` (`pureeats-notification-service/enums/PushDisplayMode.java`)

```java
enum PushDisplayMode { VISIBLE, SILENT }
```

This is the one thing PUSH needs that no other channel does — EMAIL/SMS/IN_APP are always
"visible" by definition, there's no silent email. `FcmSender.send()` branches on it: SILENT never
attaches a `Notification`/`WebpushConfig` notification block, so nothing can pop up on any
platform, on purpose. Only `data` goes out.

### `FcmPushRequest` (`pureeats-notification-service/dto/FcmPushRequest.java`)

The one shape `FcmSender.send()` accepts. Exactly one of `token`/`topic` is set. Two static
factories exist so a call site reads as intent, not a stray enum buried in a parameter list:

```java
FcmPushRequest.visible(token, topic, title, body, imageUrl, data, clickAction, actions)
FcmPushRequest.silent(token, topic, data)
```

### `FcmSender` (`pureeats-notification-service/service/FcmSender.java`)

- `send(FcmPushRequest)` — the one send method. Builds a plain `Notification` (cross-platform
  fallback) and a `WebpushConfig` (full web presentation: image, click-through link via
  `WebpushFcmOptions.withLink`, up to ~2 action buttons) when VISIBLE; only `data` when SILENT.
  `data` always carries `image`/`click_action`/`actions` (JSON-encoded) as plain strings too,
  mirrored from the visible-only fields — see the doc comment on `buildDataPayload` for why: a
  custom `onBackgroundMessage` handler (which both apps use, for consistent branding) can't
  reliably read those back off `payload.notification`/`payload.fcmOptions`, since those are meant
  for the browser's own default rendering, which a custom handler bypasses.
- `send(token, title, body, type)` — old-style convenience overload, delegates to
  `FcmPushRequest.visible(...)`. Kept because it's still the simplest form for a one-off visible
  push with no image/actions/click-link.
- `subscribeToTopic(tokens, topic)` / `unsubscribeFromTopic(tokens, topic)` — Admin SDK's
  batch subscribe, takes raw device tokens directly (no per-device client code needed).
- Without `pureeats.fcm.credentials-path` (env `FCM_CREDENTIALS_PATH`) pointing at a real
  Firebase service-account JSON, every method above just logs `[push-stub] would send ...` and
  returns — no network call to Firebase happens. This is the default (no Firebase project exists
  yet), and it's how you can develop/test everything in this doc without one: read the log line.

### Targeting: users vs topics

**Individual (`userId`)** — `PushNotificationSender` resolves every active device token for that
user via `PushTokenRepository` and sends once per token. This is the *only* mechanism for
anything user-specific: an order update, "your account was flagged," anything where the message
content is about that one person. Never invent a per-user topic (`user_42`) as a substitute — it
just re-implements this with extra subscription bookkeeping for no benefit; Firebase's own
guidance is topics don't scale as a 1:1 substitute for direct tokens.

**Broadcast (`topic`)** — for the same message going to many people at once (a promo, an
announcement). Two kinds of topic:

- **Standing audiences** (`PushAudience` enum → `PushTopics.ALL_CUSTOMERS` / `ALL_STAFF`) — every
  device is auto-subscribed to its own audience's topic the moment it registers a token (see
  `PushTokenService#save`, and `SavePushTokenRequest#audience` — the client passes `"CUSTOMER"` or
  `"STAFF"` when calling `POST /api/v1/notifications/push-token`). Sending to "every customer" is
  just `FcmSender.send(FcmPushRequest.visible(null, PushTopics.ALL_CUSTOMERS, ...))` — no loop,
  no lookup.
- **Ad-hoc segments** (`PushTopics.segment(dimension, value)`, e.g. `segment("city", "bangalore")`)
  — for anything narrower than "everyone," once a feature exists to define the segment. Nothing
  auto-subscribes devices to these; whoever builds that feature calls `subscribeToTopic` for the
  matching users' tokens first. `AdminNotificationTestController`'s `/topics/{topic}/subscribe`
  endpoint (below) is the manual version of that for testing.

### `OrderNotificationService` — the order-status entry point

Every order-status transition calls one of two overloads:

```java
notify(role, userId, title, body)                    // visible, no extra data
notify(role, userId, title, body, Map<String,Object> data)   // silent PUSH + data, IN_APP stays a normal visible bell entry
```

The 5-arg overload is what customer-facing transitions use — `data` always includes at least
`orderId` and `status`; a rider-assignment transition also includes `riderName`/`riderPhone`/
`riderPhoto`/`riderVehicleNumber`/`riderRating` (see `DeliveryOrderService#riderAssignedData`) so
the client can render who's coming without a follow-up fetch. Store-owner ("new order received")
and delivery-partner ("new delivery assigned") notifications intentionally stay on the 4-arg,
fully-visible overload — those recipients *should* be interrupted.

**Which channels actually fire is separately admin-configurable** via `NotificationRoutingService`
(Settings, stored as `notification_routing`) — defaults to `IN_APP + PUSH` for
CUSTOMER/DELIVERY_PARTNER, `IN_APP` only for STORE_OWNER/ADMIN. `silent`/`data` only change how
the PUSH channel presents itself; they ride along in the same `params` map every channel reads
from, and non-PUSH senders simply ignore keys they don't recognize.

## Client-side: consuming a push

Both apps run the identical pattern (separate files, not a shared package — these are separate
repos):

```
lib/firebaseMessaging.ts     requestPushToken() / onForegroundMessage()  (Firebase JS SDK wrapper)
hooks/usePushNotifications   registers a token app-wide on login, shows a toast for VISIBLE pushes
public/firebase-messaging-sw.js   background handler (tab not focused) + notificationclick
```

**The one rule that makes SILENT actually silent on the client:** every foreground/background
handler checks `if (!payload.notification) return` before doing anything visible. A SILENT push
never has that field — that absence *is* the "don't show anything" signal, no separate marker
needed. Other listeners (see below) still get the message regardless; they just don't touch the
toast/OS-notification.

### Order-status live sync (customer app specifics)

Two independent `onMessage` subscriptions exist simultaneously — Firebase fans the same message
out to each one, so there's no coordination needed between them:

1. **`usePushNotifications`** (app-wide, mounted in `App.tsx` via `PushNotificationBootstrap`) —
   registers the token, shows a toast for VISIBLE pushes only.
2. **`useOrderStatusUpdates`** (order tracking page only) — reloads that order's full detail on
   *any* message, silent or not; it doesn't need to inspect the payload since it's already scoped
   to one specific order.
3. **`OngoingOrderBar`** (home page) — same idea as #2: its own `onForegroundMessage` subscription
   just triggers `reload()` on the order list on *any* message, so the home banner's status label
   updates within moments of a push, instead of waiting up to 15s for its background poll.

Both #2 and #3 deliberately ignore `payload.data` and just refetch from the real API rather than
trying to hydrate UI state directly from the push payload — simpler, avoids the payload shape and
the REST response shape drifting apart, and the round-trip is cheap. The rich `data` fields
(rider name/phone/photo etc.) exist so a *future* optimization can skip the refetch if it's ever
worth it, and so the payload is self-describing when you're staring at DevTools trying to debug
one.

**Known gap:** if the tracking/home page is open but not the focused tab, `onMessage` doesn't fire
(Firebase routes to the service worker instead) and a SILENT message shows nothing anywhere by
design — the existing poll (15s on the home bar; the tracking page's own interval, backed off 6x
in PUSH mode) is what catches it up. This is an accepted tradeoff, not a bug: solving it properly
needs a service-worker-to-page relay (`postMessage`/`BroadcastChannel`), which is real extra
complexity for an edge case the polling safety net already covers within a few seconds.

## Adding a new kind of push — checklist

1. Decide the shape (table at the top). Most things are either "individual, visible" (use the
   plain `notify(role, userId, title, body)` / `FcmPushRequest.visible(...)`) or "individual,
   silent" (the 5-arg `notify(..., data)` / `FcmPushRequest.silent(...)`).
2. If it's a live-UI-sync case (silent), put everything the client needs to react in `data` —
   at minimum whatever identifies "what changed" (an id). Don't make the client guess.
3. If it's a broadcast, is it "everyone" (`PushTopics.ALL_CUSTOMERS`/`ALL_STAFF`, already wired)
   or a segment that doesn't exist yet (`PushTopics.segment(...)`, plus a subscribe step someone
   has to build)?
4. On the client, if the client needs to *do* something with a silent push beyond what
   `usePushNotifications` already does (just registration + visible toasts), add your own
   `onForegroundMessage` subscription near wherever that data actually lives — see
   `OngoingOrderBar`/`useOrderStatusUpdates` for the pattern. Don't route it through the shared
   toast hook; that hook is specifically "things worth interrupting the user for."

## Testing via Postman/curl

Auth is OTP-based (no password login) — see `AdminNotificationTestController`, currently
`@PreAuthorize`'d to ADMIN/SUPER_ADMIN (re-check that annotation is actually active before relying
on this being protected — it's been toggled during development).

```bash
# 1. Get a token (any ADMIN/SUPER_ADMIN account)
curl -X POST http://localhost:8081/api/v1/auth/otp/send \
  -H "Content-Type: application/json" -d '{"method":"EMAIL","email":"superadmin@pureeats.local"}'
curl -X POST http://localhost:8081/api/v1/auth/otp/verify \
  -H "Content-Type: application/json" -d '{"challengeId":"<from above>","otp":"<from email>"}'
# -> use `accessToken` as the Bearer token below

# 2. Visible push, with an image, click-link and data - to one user (also writes to their bell)
curl -X POST http://localhost:8081/api/v1/admin/notifications/test/push \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{
    "userId": 22,
    "title": "Your order is out for delivery",
    "body": "Rider is 5 minutes away",
    "imageUrl": "https://picsum.photos/400/200",
    "clickAction": "http://localhost:5273/orders/104",
    "data": { "orderId": "104" }
  }'

# 3. Silent push - no popup anywhere, no bell entry, data only
curl -X POST http://localhost:8081/api/v1/admin/notifications/test/push \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{ "userId": 22, "silent": true, "data": { "orderId": "104", "status": "ON_THE_WAY" } }'

# 4. Broadcast - subscribe (normally automatic on login, this is the manual/testing path).
curl -X POST http://localhost:8081/api/v1/admin/notifications/topics/all_customers/subscribe \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" -d '{ "userId": 22 }'
curl -X POST http://localhost:8081/api/v1/admin/notifications/test/push \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{ "topic": "all_customers", "title": "Weekend offer", "body": "20% off, today only" }'

# 5. Web action buttons (Chrome/Edge only - other browsers just ignore them)
curl -X POST http://localhost:8081/api/v1/admin/notifications/test/push \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{
    "userId": 22, "title": "Rate your order", "body": "How was PureEats today?",
    "actions": [{ "action": "rate_5", "title": "★★★★★" }, { "action": "rate_low", "title": "Not great" }]
  }'
```

## Gotchas / known limitations

- **Stub mode**: without `FCM_CREDENTIALS_PATH` set (and the backend restarted after setting it —
  it's read once at `@PostConstruct`), nothing is ever really sent; every call just logs
  `[push-stub] would send ...`. Check the startup log for `FCM push enabled (credentials loaded
  from ...)` to confirm it actually initialized.
- **A device token only exists after successful registration** — that needs Firebase config
  complete (Admin Panel → Settings → Push Notifications, picked up automatically by both apps at
  runtime) *and* the SAME values hand-copied into both `public/firebase-messaging-sw.js` files
  (they can't read the backend config - see the comment at the top of each), *and* the browser
  permission prompt actually granted. `requestPushToken()` degrades to `null` silently on any
  failure in this chain by design — check the `[push]`-prefixed console logs it emits at each
  step if a token isn't showing up.
- **Action buttons**: Chrome/Edge render at most ~2; Firefox and Safari ignore `actions` entirely
  and just show the plain notification. Don't rely on them for anything critical.
- **Topic subscription isn't instant** — Firebase documents it can take a little time to
  propagate; don't expect a `subscribeToTopic` call to be immediately followed by a successful
  send reaching that exact device in an automated test.
- **`databaseURL`** (Firebase Realtime Database) is intentionally not part of any config here —
  it's unrelated to Cloud Messaging and nothing in this app uses Realtime Database.
