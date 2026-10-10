# Customer ETA: the slow-down buffer algorithm

How PureEats calculates an order's ETA, and why the customer app's countdown runs slower than real time.

Without a buffer, the countdown would track real time exactly. Any traffic jam or slow kitchen would push the ETA past zero, or force it to jump back up, for example from 2 min to 9 min. That erodes trust. The buffer slows the customer's countdown so that small delays are absorbed and the number on screen only ever moves down.

> **Scope:** the slow-down applies **only to the customer app**. The rider and restaurant apps show **real-time** countdowns, because they need the true numbers to do their jobs.

---

## Step 1: Collect the three base parameters when the order is placed

When a new order is created, the backend (`OrderTimingService.initialise`) stores three numbers on the order:

| Param | Meaning | Source | Example |
|---|---|---|---|
| **T1** | Preparation time | The restaurant's own `preparation_time`. If it isn't set, the **Default preparation time** setting is used (default 20). | 30 min |
| **T2** | Rider-to-restaurant buffer | The **Rider to restaurant** setting (default 10). | 10 min |
| **T3** | Restaurant-to-customer travel time | The configured `DistanceCalculator`: the Google Distance Matrix with live traffic when `pureeats.distance.provider=google` and an API key are set, otherwise an estimate from the distance at about 25 km/h. The minimum is 1. | 50 min |

Columns: `restaurants.preparation_time`, `orders.prepare_time` (T1), `orders.rider_to_restaurant_minutes` (T2) and `orders.travel_minutes` (T3).

**Self-pickup orders:** T2 = 0 and T3 = 0, because no rider and no travel are involved.

## Step 2: Compute the base ETA

```
Total base ETA = T1 + T2 + T3
```

Example: 30 + 10 + 50 = **90 min**. This is stored as `orders.eta_minutes`. It is **fixed at placement** and never recalculated, which is what keeps the customer's countdown stable.

## Step 3: Send the ETA and the slow-down factor to the customer app

Customer order responses (the order detail, plus the summary used by the ongoing-order bar) include:

- `createdAt`: when the order was placed. This is the countdown's start.
- `etaMinutes`: the base ETA from step 2.
- `etaSlowdownFactor`: the **Customer ETA slowdown** setting (`customer_eta_slowdown_factor`, default **1.5**).

The backend (`OrderTimingService.customerSlowdown`) only accepts factors from **1 to 5**. Anything else, including blank or non-numeric values, falls back to 1.5.

| Factor | Meaning |
|---|---|
| 1.0 | No buffer: the countdown runs in real time |
| 1.5 | 1.5 real seconds take 1 second off the countdown (about 50% slower) |
| 2.0 | "2 s real = 1 s countdown" |

## Step 4: Compute the buffered remaining time every second

The customer app (`src/lib/eta.ts → bufferedRemainingSeconds`) runs this every second:

```
elapsedReal = now − createdAt                          (seconds, never negative)
remaining   = etaMinutes × 60 − elapsedReal / factor   (seconds)
display     = max(60, round(remaining))
```

In words, real elapsed time is **divided by the factor** before it is subtracted. The countdown therefore loses one second for every `factor` real seconds.

Because the result is a pure function of `now`, the countdown is the same after a page refresh, an app restart or a device switch. Nothing is stored on the client.

## Step 5: Apply the floor ("Arriving soon")

The value never drops below **60 seconds** (`ARRIVING_SOON_SECONDS`). Once it reaches that floor, the app shows **"Arriving soon"** instead of a number. The customer never sees `0:00` or a negative time, even if the order runs very late.

## Step 6: Display it

- **Order tracking page:** "Arriving in `h:mm:ss`" (`formatEtaClock`), then "Arriving soon". Self-pickup orders show "Ready in …".
- **Ongoing-order bar:** "· ~N min" (rounded up), then "· arriving soon".

## Step 7: Stop the countdown when the order ends

The countdown only runs while the order is in an active status. Once it's **delivered** (or cancelled), the timer stops and the status takes over.

---

## Worked example: base ETA 90 min

The value shown at each point in real time after the order is placed:

| Real time elapsed | Factor 1.0 (no buffer) | Factor **1.5** | Factor 2.0 |
|---|---|---|---|
| 0 min | 90 min | 90 min | 90 min |
| 15 min | 75 | 80 | 82.5 |
| 30 min | 60 | **70** | 75 |
| 60 min | 30 | 50 | 60 |
| 90 min (on-time delivery) | 0 → *arriving soon* | 30 | 45 |
| 105 min (15 min late) | *arriving soon* | 20 | 37.5 |
| 135 min | *arriving soon* | *arriving soon* | 22.5 |
| 180 min | *arriving soon* | *arriving soon* | *arriving soon* |

**Rule of thumb:** the countdown reaches "Arriving soon" after about `ETA × factor` real minutes. That is 135 min at 1.5 and 180 min at 2.0.

## Why this prevents ETA jumps

1. **It's monotonic.** `remaining` only ever decreases, and it never resets or increases.
2. **It absorbs delays.** A 15-minute traffic jam doesn't change what is shown, because the slowed timer still has time left.
3. **It doesn't need a recalculation.** Mid-trip changes such as the rider's live ETA don't feed into the customer timer, so a new route estimate can't make the number suddenly jump.
4. **It ends gracefully.** In the worst case the screen settles on "Arriving soon" rather than an overdue or negative time.

## Trade-off and tuning

A larger factor means fewer "late" experiences. But an order that arrives on time will still show time remaining. At 1.5, a 90-minute order delivered on time still shows about 30 min left. Customers usually read that as "it came early". Even so, keep the factor modest:

- **1.2–1.5** is the recommended range for most cities.
- Use **2.0** only where traffic is very unpredictable.
- To change it, go to Admin → Settings → General → *Delivery time estimates* → **Customer ETA slowdown**. The new value applies to everything the app fetches from then on, including orders already in progress.

The base ETA's own accuracy matters more than the factor. Set a realistic **T1** for each restaurant in its admin form, and enable Google travel time on the server for **T3**.

## What the other apps show (real time, no buffer)

| App | Countdown | Target |
|---|---|---|
| Restaurant | "Food ready in" (T1) | `prepDueAt` = restaurant accepted + T1. It turns negative (late) once that time passes. |
| Rider, before arriving | "Reach the restaurant in" (T2) | `reachRestaurantBy` = rider accepted + T2 |
| Rider, at the restaurant, food not ready | "Food ready in" / "Kitchen is running late by" | `pickupDueAt` (same as `prepDueAt`) |
| Rider, after pickup | "Drop-off ETA (live)" | `GET /api/v1/delivery/orders/{id}/eta`, using the rider's last position. It refreshes every 30 s. |

## Code map

| Piece | File |
|---|---|
| T1/T2/T3, ETA, slowdown and `prepDueAt` | `pureeats-order-service/.../service/OrderTimingService.java` |
| Settings and their defaults | `SettingSchemaService` (`default_prep_time_minutes`, `rider_to_restaurant_minutes`, `customer_eta_slowdown_factor`) |
| Google travel time with traffic | `pureeats-geo-service/.../GoogleDistanceMatrixCalculator.java` (`etaMinutes`) |
| Customer buffer | customer app `src/lib/eta.ts` |
| Tests | `OrderTimingServiceTest` |
