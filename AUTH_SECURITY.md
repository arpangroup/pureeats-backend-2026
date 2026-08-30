# OTP-Based Authentication & Security Subsystem

This document covers the OTP-challenge authentication flow — signup, phone/email OTP login,
tokens, sessions, and the security mechanisms around them. It is now the **only** authentication
flow in this codebase: the password-login/register and 4-digit phone-OTP endpoints that originally
existed alongside it have been removed entirely (not deprecated — deleted, along with
`AuthService`, `OtpService`, the `SmsOtp` table, and their DTOs). One consequence: the still-present
`PasswordResetController`/`PasswordResetService` (email-based password reset) has nothing left to
plug into — nothing checks a password at login anymore. See
[pureeats-user-service/README.md](pureeats-user-service/README.md) for the full request/response
contracts and a step-by-step execution timeline; this document covers the cross-cutting
architecture and deployment/config. For the rest of the API, module graph, and entity
relationships, see [README.md](README.md).

## Table of contents

- [Architecture](#architecture)
- [Auth flow](#auth-flow)
- [API reference](#api-reference)
- [Token strategy](#token-strategy)
- [OTP security policy](#otp-security-policy)
- [Blocklist](#blocklist)
- [Rate limiting](#rate-limiting)
- [Notification architecture](#notification-architecture)
- [Gmail SMTP setup](#gmail-smtp-setup)
- [SMS provider setup](#sms-provider-setup)
- [Database tables](#database-tables)
- [Configuration reference](#configuration-reference)
- [Environment variables](#environment-variables)
- [Privacy / PII handling](#privacy--pii-handling)
- [Testing](#testing)
- [Admin audit endpoints](#admin-audit-endpoints)
- [Production recommendations](#production-recommendations)

---

## Endpoints at a glance

All under `/api/v1/auth`, all public except `/logout-all`:

| Endpoint | Purpose |
|---|---|
| `POST /register` | Email signup → sends a verification OTP |
| `POST /otp/send` | Start a phone/email OTP login challenge |
| `POST /otp/verify` | Verify a challenge's OTP → access + refresh token |
| `POST /otp/resend` | Resend the OTP for an existing challenge |
| `POST /refresh` | Rotate a refresh token for a new access token |
| `POST /logout` | Revoke one session |
| `POST /logout-all` 🔒 | Revoke every session for the current user |

A phone or email that has never been used before is **auto-registered** on first successful OTP
verification against `/otp/send` + `/otp/verify` — telling a client "no account with that number"
before they've proven they own it would be an account-enumeration leak.

## Architecture

Every piece is a small, single-responsibility, interface-first component — no god service.
Package layout (new code only):

```
domain/enums/             AccountStatus only - kept here (not moved to pureeats-user-service)
                          because `User` (a shared domain entity every module depends on) has an
                          `accountStatus` field of this type; domain can't import from a module
                          that itself depends on domain, so this one has to stay put.
domain/common/            PiiMaskUtil, RequestIdContext

pureeats-notification-service/
  entity/                 NotificationLog - used exclusively by this module
  enums/                  NotificationType, NotificationChannel, NotificationStatus - used by this
                          module AND pureeats-user-service (which already depends on this module,
                          so referencing them back is not a cycle)
  provider/               EmailProvider, SmsProvider + Smtp/Console implementations
  template/               TemplateRenderer, NotificationTemplateResolver
  service/                NotificationService (the one interface auth code depends on),
                          EmailNotificationService, SmsNotificationService,
                          NotificationDispatcherService
  config/                 NotificationProperties, NotificationProviderConfig

pureeats-user-service/
  entity/                 OtpChallenge, SecurityBlockEntry, UserDevice, LoginHistory,
                          UserSession, AuditLog, RateLimitBucket - all used exclusively by this
                          module, so they live here rather than in the shared `domain` kernel
                          (picked up by a second @EntityScan package on PureEatsApplication)
  enums/                  AuthenticationMethod, OtpChallengeStatus, BlockType, BlockStatus,
                          LoginMethod, SecurityEventType - same reasoning as the entities above
  otp/                    OtpGenerator, OtpHasher, OtpChallengeService (the OTP lifecycle -
                          create/resend/verify - knows nothing about tokens/notifications/users)
  security/blocklist/     BlocklistService
  security/ratelimit/     RateLimiter (DB-backed fixed-window counter)
  security/geolocation/   IpGeolocationService
  security/device/        DeviceService, LoginHistoryRecorder
  security/session/       SessionService (refresh-token issuance/rotation/revocation)
  security/audit/         SecurityEvent, SecurityEventPublisher
  security/metadata/      RequestMetadata, RequestMetadataResolver, UserAgentParser
  service/AuthenticationService   the thin orchestrator the controller calls
  service/UserProvisioningService the one place a password-less User row is created
  config/AuthSecurityProperties   every configurable knob (`security.*`)

pureeats-app/
  filter/CorrelationIdFilter      X-Request-ID in/out, feeds RequestIdContext
```

`AuthenticationService` is deliberately thin: it validates the request shape, checks the
blocklist, enforces rate limits, delegates to `OtpChallengeService` for the actual OTP lifecycle,
asks `NotificationService` to deliver it, and on success asks `SessionService`/`JwtTokenProvider`
for tokens. Every one of those collaborators is swappable independently:

| Interface | Default implementation | Swap in later |
|---|---|---|
| `OtpGenerator` | `SecureOtpGenerator` (SecureRandom, numeric) | any other secure generator |
| `OtpHasher` | `PasswordEncoderOtpHasher` (reuses the app's BCrypt bean) | a dedicated OTP hashing scheme |
| `NotificationService` → `EmailProvider` | `ConsoleEmailProvider` | `SmtpEmailProvider` (Gmail — see below), then SES/SendGrid |
| `NotificationService` → `SmsProvider` | `ConsoleSmsProvider` | Twilio/MSG91/AWS SNS — implement `SmsProvider`, wire behind `notification.sms-provider` |
| `IpGeolocationService` | `HttpIpGeolocationService` (ip-api.com, free tier) | a paid provider, same interface |
| `RateLimiter` | `DatabaseRateLimiter` (pessimistic-lock counter table) | a Redis-backed implementation, once Redis is introduced to this app |
| `BlocklistService` | `JpaBlocklistService` | unchanged unless you need a distributed cache in front of it |
| `SecurityEventPublisher` | `AuditLogSecurityEventPublisher` (writes `audit_logs`) | Kafka/SNS/SIEM sink |

## Auth flow

```
Client                                    Server
  |--- POST /auth/register -------------->|  create User (unverified) + OtpChallenge (SIGNUP_OTP)
  |                                       |  NotificationService.send(...)  [OTP never in response]
  |<---- { challengeId, maskedDest } -----|
  |                                       |
  |--- POST /auth/otp/send --------------->|  (for an existing/new phone or email)
  |                                       |  blocklist + rate-limit checks
  |                                       |  create OtpChallenge (LOGIN_OTP), send notification
  |<---- { challengeId, maskedDest } -----|
  |                                       |
  |--- POST /auth/otp/verify ------------>|  OtpChallengeService.verify (expiry/attempts/lock)
  |                                       |  mark email/phone verified, provision User if new
  |                                       |  DeviceService + LoginHistoryRecorder + SessionService
  |<-- { accessToken, refreshToken } -----|
  |                                       |
  |--- POST /auth/refresh --------------->|  rotate refresh token (old one now dead)
  |<-- { accessToken, refreshToken } -----|
  |                                       |
  |--- POST /auth/logout ----------------->|  revoke that one session
  |--- POST /auth/logout-all (🔒) -------->|  revoke every session for the user
```

`/otp/resend` re-enters the same challenge: validates cooldown + max-resend count, generates a
brand-new OTP (the old one stops working immediately), resets the attempt counter, and re-sends.

## API reference

Base path `/api/v1/auth`. Response envelope is the same `{ success, message, data, timestamp,
errorCode, requestId }` used everywhere else in this API — `errorCode`/`requestId` are new,
additive fields (existing clients reading only `success`/`message`/`data` are unaffected). Field
names are **camelCase**, matching every other endpoint in this codebase (not the snake_case shown
in the original design brief).

### `POST /signup`
```json
{ "fullName": "John Doe", "email": "john@gmail.com" }
```
→ `201`/`200` `{ "success": true, "data": { "challengeId": "...", "maskedDestination": "j**n@gmail.com", "expiresIn": 600, "resendAvailableIn": 30 } }`
Errors: `EMAIL_ALREADY_REGISTERED` (409).

### `POST /otp/send`
```json
{ "method": "PHONE", "countryId": 1, "phone": "9876543210" }
```
or
```json
{ "method": "EMAIL", "email": "john@gmail.com" }
```
→ same `LoginChallengeResponse` shape as signup.

### `POST /otp/verify`
```json
{ "challengeId": "886998e2-...", "otp": "239843" }
```
→ `200` `{ "success": true, "data": { "accessToken": "...", "refreshToken": "...", "tokenType": "Bearer", "expiresIn": 900 } }`

Errors:
- `INVALID_OTP` (400) — `data.attemptsRemaining` tells the client how many guesses are left.
- `OTP_ATTEMPTS_EXCEEDED` (400) — challenge is now `LOCKED`; the client must call `/otp/send` (or `/otp/resend`, if still allowed) again.
- `OTP_EXPIRED` (400), `CHALLENGE_NOT_FOUND` (400), `ALREADY_VERIFIED` (400).
- `ACCOUNT_LOCKED` / `ACCOUNT_BLOCKED` / `ACCOUNT_DISABLED` / `ACCOUNT_DEACTIVATED` (403) — the OTP was right, but the account itself can't be used right now.

### `POST /otp/resend`
```json
{ "challengeId": "886998e2-..." }
```
→ `200` `{ "success": true, "message": "A new OTP has been sent.", "data": { "expiresIn": 600, "resendAvailableIn": 30 } }`
Errors: `RESEND_COOLDOWN` (429), `MAX_RESENDS_EXCEEDED` (429), `ALREADY_VERIFIED`/`CHALLENGE_CANCELLED` (400).

### `POST /refresh`
```json
{ "refreshToken": "..." }
```
→ same shape as `/otp/verify`'s success response — **rotated**: the old refresh token is dead the instant this call succeeds. Presenting an already-rotated (or already-logged-out) token returns `401 REFRESH_TOKEN_REUSE_DETECTED` and — as a compromise response — revokes every session that user has.

### `POST /logout`
```json
{ "refreshToken": "..." }
```
Public (the refresh token itself is the credential) — revokes that one session.

### `POST /logout-all` 🔒
No body — revokes every session for the caller (identified by their access token). This is the
one new endpoint that requires `Authorization: Bearer <accessToken>`; every other endpoint above
is intentionally public (that's the whole point of a login flow).

## Token strategy

- **Access token**: JWT (`AuthenticatedUser`: userId, name, email, phone, role,
  deliveryGuyDetailId claims), short-lived via `security.session.access-token-expiry-minutes`
  (default 15). Stateless — never persisted, never revocable individually (that's what the short
  lifetime is for). `JwtTokenProvider` also has a `pureeats.jwt.expiration-ms`-driven default-expiry
  overload, kept for test/utility convenience — the live flow always passes the explicit
  short-lived expiry instead.
- **Refresh token**: an opaque, cryptographically random 256-bit string, returned once. The server
  only ever stores its SHA-256 hash (`user_sessions.refresh_token_hash`) — a stolen database dump
  cannot be replayed into a live session. `security.session.refresh-token-expiry-days` (default 30).
- **Rotation**: every `/refresh` call revokes the presented token and issues a new one
  (`SessionService.rotate`). Reusing an already-rotated token is treated as a signal of theft and
  revokes every session belonging to that user (`REFRESH_TOKEN_REUSE_DETECTED`).
- **Delivery**: refresh tokens are returned in the **JSON response body**, not a cookie. This
  repo's `SecurityConfig.corsConfigurationSource()` currently allows `*` origins with
  `allowCredentials(false)` — a browser will not send/receive cookies under that policy, so cookie
  delivery isn't actually available without first tightening CORS to a specific origin with
  credentials enabled. Trade-off: body-delivered tokens are readable by any JS on the page (XSS
  risk) but need no CORS/cookie rework; `httpOnly` cookies are the better long-term answer for a
  browser client but require locking CORS down to your real frontend origin(s) first. If/when
  that CORS tightening happens, moving the refresh token into a `Secure; HttpOnly; SameSite=Strict`
  cookie set by `/otp/verify` and `/refresh` (and cleared by `/logout`) is a contained follow-up —
  it only touches `AuthController`, not `AuthenticationService`/`SessionService`.
- **Session visibility/revocation**: `user_sessions` is the source of truth for "what devices are
  logged in" — a future `GET /auth/sessions` (list) is a straightforward read on top of the
  existing `UserSessionRepository.findByUserIdAndRevokedAtIsNull`.

## OTP security policy

All configurable under `security.otp.*` (see [Configuration reference](#configuration-reference)):

| Rule | Default | Enforced by |
|---|---|---|
| OTP length | 6 digits | `SecureOtpGenerator` |
| OTP validity | 10 minutes | `OtpChallengeService` |
| Max verification attempts | 5 | `OtpChallengeService.verify` → `LOCKED` status once exceeded |
| Resend cooldown | 30 seconds | `OtpChallengeService.resend` |
| Max resends per challenge | 3 | `OtpChallengeService.resend` |
| Max OTP requests per destination/hour | 10 | `RateLimiter` (`otp-requests:*:dest:*`) |
| Max OTP requests per IP/hour | 20 | `RateLimiter` (`otp-requests:*:ip:*`) |

The OTP itself is **never** logged, returned in any API response, or stored — only a BCrypt hash
(`otp_challenges.otp_hash`) is persisted, via the app's existing `PasswordEncoder` bean (no new
hashing dependency introduced). Verification/resend run in their own transaction with
`noRollbackFor` so a "wrong guess" or "hit the resend limit" outcome still commits its own attempt
counter / lock / resend-count update — see the comments on `OtpChallengeService.verify`/`resend`.

Concurrency: `OtpChallengeRepository.findWithLockByChallengeId` takes a pessimistic write lock, so
two simultaneous verify (or resend) calls against the same challenge serialize instead of racing
the attempt counter.

## Blocklist

`security_blocklist` supports blocking by `IP`, `DEVICE`, `EMAIL`, `PHONE`, or `USER`, each
optionally time-limited (`expiresAt = null` → permanent) with a `reason` and `createdBy` for
audit. Checked before every OTP-issuing operation (`AuthenticationService.assertNotBlocked`) for
IP, device, and the destination being used. There's no admin API to manage entries yet — insert
rows directly (or add a small internal admin endpoint later); `BlocklistService.block(...)` is
already there to call from one.

## Rate limiting

`RateLimiter` is a fixed-window counter backed by `rate_limit_buckets` (one row per key + window
start), mutated under a pessimistic row lock — correct under concurrent requests, and correct
across multiple app instances sharing one database, without needing Redis. Every OTP-issuing
endpoint checks **two dimensions**, not one: IP and destination (email/phone) for login, plus
destination+IP-scoped OTP-request quotas layered on top for signup/login/resend. If this app
later adopts Redis for other reasons, replacing `DatabaseRateLimiter` with a Redis-backed
`RateLimiter` implementation is the only change needed — every call site depends on the interface.

## Notification architecture

`AuthenticationService` never imports `JavaMailSender`, `EmailProvider`, or `SmsProvider` — it
only calls `NotificationService.send(NotificationRequest)`. That request carries a
`NotificationType` (`LOGIN_OTP`, `SIGNUP_OTP`, `PASSWORD_RESET_OTP`, `EMAIL_VERIFICATION`,
`PHONE_VERIFICATION`) and a channel (`EMAIL`/`SMS`); `NotificationDispatcherService` routes it to
`EmailNotificationService` or `SmsNotificationService`, which render a template
(`NotificationTemplateResolver` + `SimpleTemplateRenderer`, `{{param}}` substitution) and call the
configured `EmailProvider`/`SmsProvider`. Every attempt (success or failure) is logged to
`notification_logs` — **never** the message body or OTP, only a masked destination and outcome.

Templates live under `pureeats-notification-service/src/main/resources/templates/{email,sms}/`.
`otp.html`/`otp.txt`/`sms/otp.txt` are the generic fallback used by every OTP-shaped
`NotificationType` today; drop in `login-otp.html`, `signup-otp.html`, etc. (matching the
lower-kebab-case of the enum name) to give a specific type its own wording without touching any
Java code. Subjects come from `templates/email/subjects.properties`.

`TemplateRenderer` is a one-method interface specifically so a heavier engine (Thymeleaf,
FreeMarker) can replace `SimpleTemplateRenderer` later without changing
`EmailNotificationService`/`SmsNotificationService`.

## Gmail SMTP setup

By default `notification.email-provider=console` — no email is actually sent; the rendered
message is printed to stdout (clearly dev-only, see `ConsoleEmailProvider`'s Javadoc). To send
real email via Gmail:

1. **Do not use your normal Gmail password** — Google blocks plain-password SMTP for third-party
   apps. Create an **App Password** instead:
   - Enable 2-Step Verification on the Google account (required for App Passwords to be offered).
   - Go to [myaccount.google.com/apppasswords](https://myaccount.google.com/apppasswords), create
     one for "Mail" / "Other (Custom name)", and copy the 16-character password shown once.
2. Set environment variables (never commit real credentials):
   ```bash
   NOTIFICATION_EMAIL_PROVIDER=smtp
   MAIL_HOST=smtp.gmail.com
   MAIL_PORT=587
   MAIL_USERNAME=your.account@gmail.com
   MAIL_PASSWORD=the16charapppassword
   ```
3. That's it — `application.yml`'s `spring.mail.*` block already sets
   `spring.mail.properties.mail.smtp.auth=true` and `starttls.enable=true` (Gmail requires both).
   `NotificationProviderConfig` only creates the `SmtpEmailProvider` bean when
   `notification.email-provider=smtp`, so the console provider is never accidentally left active
   in an environment that *did* configure real credentials — you have to opt in explicitly.

For a production sender identity independent of a personal Gmail inbox, use a Google Workspace
mailbox's app password the same way, or swap `EmailProvider` for `AwsSesEmailProvider`/SendGrid
later (implement the interface, register it behind a new `notification.email-provider` value).

## SMS provider setup

No SMS gateway credentials exist in this codebase. `notification.sms-provider=console` (default)
prints the message instead of sending it — the same accepted dev-only trade-off as the console
email provider. To go live, implement `SmsProvider` (one method:
`NotificationResult send(String phoneNumber, String message)`) against Twilio/MSG91/AWS SNS/etc.,
register it as a `@Bean` gated by `@ConditionalOnProperty(prefix = "notification", name =
"sms-provider", havingValue = "twilio")` (mirroring `NotificationProviderConfig`'s existing
pattern), and set `NOTIFICATION_SMS_PROVIDER=twilio`. `SmsNotificationService` needs no changes.

## Database tables

All created by Hibernate (`ddl-auto=update`, matching this project's existing convention — no
Flyway/Liquibase). Reference DDL below for anyone running with `ddl-auto=validate` in production;
generate it authoritatively with `ddl-auto=create` against a throwaway database and diff instead
of hand-copying this.

| Table | Purpose | Key indexes |
|---|---|---|
| `otp_challenges` | One row per OTP send/resend/verify cycle | unique `challenge_id`; `user_id`; `destination`; `expires_at` |
| `security_blocklist` | IP/device/email/phone/user blocks | `(block_type, block_value)` |
| `user_devices` | Best-effort device fingerprint seen per user | unique `(user_id, device_id)`; `device_id` |
| `login_history` | Append-only login attempt audit trail | `user_id`; `ip_address` |
| `user_sessions` | Live refresh-token sessions | unique `session_id`; unique `refresh_token_hash`; `user_id` |
| `audit_logs` | Every `SecurityEventType` (signup/login/otp/token/lock/etc.) | `user_id`; `event_type`; `created_at` |
| `rate_limit_buckets` | Fixed-window counters backing `RateLimiter` | unique `(bucket_key, window_start)` |
| `notification_logs` | Delivery attempt outcome (never the message body) | `created_at` |

`users` gains: `phone_verified_at` (mirrors the existing `email_verified_at` — a nullable
timestamp is the single source of truth for "verified", no separate boolean needed),
`account_status` (`ACTIVE`/`TEMPORARILY_LOCKED`/`BLOCKED`/`DISABLED`, independent of the legacy
`is_active` string column), `locked_at`, `locked_until`, `lock_reason`. `password` is now
nullable — OTP-only accounts never had one; this was silently tolerated by MySQL's default
(non-strict) `sql_mode` before and is now explicit and correct.

## Configuration reference

All under `security.*`/`notification.*` in `application.yml` — see that file for the full
`${ENV_VAR:default}` list. Nested structure:

```yaml
security:
  otp: { length, expiry-minutes, max-attempts, resend-cooldown-seconds, max-resends,
         max-requests-per-destination-per-hour, max-requests-per-ip-per-hour }
  session: { access-token-expiry-minutes, refresh-token-expiry-days }
  rate-limit: { login-ip, login-destination, verify-ip, resend-ip }   # each: { limit, window-seconds }
  geolocation: { enabled, provider, timeout-ms, cache-ttl-minutes }
notification:
  email-provider: console | smtp
  sms-provider: console
  from-address, from-name
```

## Environment variables

| Env var | Default | Purpose |
|---|---|---|
| `OTP_LENGTH` | `6` | OTP digit count |
| `OTP_EXPIRY_MINUTES` | `10` | OTP validity |
| `OTP_MAX_ATTEMPTS` | `5` | Wrong guesses allowed before a challenge locks |
| `OTP_RESEND_COOLDOWN_SECONDS` | `30` | Minimum gap between resends |
| `OTP_MAX_RESENDS` | `3` | Resends allowed per challenge |
| `OTP_MAX_REQUESTS_PER_DESTINATION_PER_HOUR` | `10` | New-challenge cap per phone/email |
| `OTP_MAX_REQUESTS_PER_IP_PER_HOUR` | `20` | New-challenge cap per IP |
| `ACCESS_TOKEN_EXPIRY_MINUTES` | `15` | Access token lifetime for the OTP-challenge flow |
| `REFRESH_TOKEN_EXPIRY_DAYS` | `30` | Refresh token / session lifetime |
| `RATE_LIMIT_LOGIN_IP_LIMIT` / `_WINDOW_SECONDS` | `10` / `60` | `/otp/send` per-IP limit |
| `RATE_LIMIT_LOGIN_DESTINATION_LIMIT` / `_WINDOW_SECONDS` | `5` / `60` | `/otp/send` per-destination limit |
| `RATE_LIMIT_VERIFY_IP_LIMIT` / `_WINDOW_SECONDS` | `20` / `60` | `/otp/verify` per-IP limit |
| `RATE_LIMIT_RESEND_IP_LIMIT` / `_WINDOW_SECONDS` | `10` / `60` | `/otp/resend` per-IP limit |
| `GEOLOCATION_ENABLED` | `true` | Set `false` to skip IP geolocation entirely |
| `GEOLOCATION_PROVIDER` | `ip-api` | Informational today (only one implementation exists) |
| `GEOLOCATION_TIMEOUT_MS` | `2000` | HTTP timeout for the geolocation call |
| `GEOLOCATION_CACHE_TTL_MINUTES` | `60` | How long a resolved IP is cached in-memory |
| `NOTIFICATION_EMAIL_PROVIDER` | `console` | `console` or `smtp` |
| `NOTIFICATION_SMS_PROVIDER` | `console` | `console` (no real gateway wired up) |
| `NOTIFICATION_FROM_ADDRESS` / `NOTIFICATION_FROM_NAME` | `no-reply@pureeats.local` / `PureEats` | Email "From" header |
| `MAIL_HOST` / `MAIL_PORT` / `MAIL_USERNAME` / `MAIL_PASSWORD` | `smtp.gmail.com` / `587` / *(empty)* | Only read when `NOTIFICATION_EMAIL_PROVIDER=smtp` — see [Gmail SMTP setup](#gmail-smtp-setup) |
| `SUPER_ADMIN_NAME` / `SUPER_ADMIN_EMAIL` / `SUPER_ADMIN_PASSWORD` | `Super Admin` / `superadmin@pureeats.local` / dev placeholder | Read once, at startup, by `SuperAdminSeeder` — see `pureeats-user-service/README.md`'s [Privileged roles](../pureeats-user-service/README.md#privileged-roles-super_admin-and-admin) section. **Override the password before any shared deployment.** |

## Privacy / PII handling

- **Masking**: `PiiMaskUtil.maskEmail`/`maskPhone`/`maskDestination` (`domain.common`) mask every
  email/phone that leaves the OTP endpoints (`maskedDestination` in responses) and every
  destination written to `notification_logs`.
- **Never logged**: raw OTP, password, access token, refresh token. `notification_logs` stores
  only `destinationMasked`; `SessionService` stores only a SHA-256 hash of the refresh token;
  `OtpChallenge` stores only a BCrypt hash of the OTP.
- **Correlation id**: `CorrelationIdFilter` stamps every request with `X-Request-ID` (generated if
  the client didn't send one), exposed via `RequestIdContext` and included in every error
  response and audit-log row for tracing.

## Testing

- **Unit** (no Spring context, Mockito): `SecureOtpGeneratorTest`, `PiiMaskUtilTest`, and
  `OtpChallengeServiceTest` — the last covers success, wrong-OTP-decrements-attempts, lock-after-
  max-attempts, expiry, unknown challenge, resend cooldown, resend limit, and resend resetting
  attempts.
- **Integration** (`pureeats-app`, H2 in-memory + real `MockMvc` HTTP calls,
  `AuthFlowIntegrationTest`): full signup → verify → refresh (with rotation-reuse rejection) →
  logout flow; wrong-OTP → `attemptsRemaining` → eventual lock; resend invalidating the previous
  code. `NotificationService` is `@MockitoBean`-replaced so no real email/SMS is ever sent during
  tests — the plaintext OTP is captured from the mock's invocation the same way a real inbox would
  receive it, never from an API response.
- Run everything: `mvn test` from the repo root. Run just this subsystem's tests:
  `mvn -pl domain,pureeats-notification-service,pureeats-user-service,pureeats-app -am test`.
- **`AdminAuditControllerTest`**: confirms a `CUSTOMER` token is rejected and `ADMIN`/`SUPER_ADMIN`
  tokens succeed on all seven admin audit endpoints (see below), and that the OTP-challenge/
  session views never serialize a hash.

## Admin audit endpoints

Every table this document describes (`otp_challenges`, `security_blocklist`, `user_devices`,
`login_history`, `user_sessions`, `audit_logs`, `rate_limit_buckets`) has a read-only, paginated
`GET` endpoint under `/api/v1/admin/*`, restricted to `ADMIN`/`SUPER_ADMIN` — full reference in
[pureeats-user-service/README.md → Admin audit endpoints](pureeats-user-service/README.md#admin-audit-endpoints).
Authorization is layered two independent ways there (URL-pattern rule + method-level
`@PreAuthorize`), which is also where the trade-offs of adding method-level security to this
codebase are written up.

## Production recommendations

- Set `NOTIFICATION_EMAIL_PROVIDER=smtp` with real Gmail/SES credentials and implement a real
  `SmsProvider` before relying on this in production — the console providers are dev-only by
  design (they print instead of sending).
- Override `JWT_SECRET` (already required) and pick `ACCESS_TOKEN_EXPIRY_MINUTES`/
  `REFRESH_TOKEN_EXPIRY_DAYS` deliberately for your risk tolerance.
- Override `SUPER_ADMIN_PASSWORD` before any shared deployment — `SuperAdminSeeder` only ever
  creates the account if none exists, so the dev placeholder password will otherwise sit valid in
  production indefinitely.
- Consider `JPA_DDL_AUTO=validate` once the schema has stabilized, using the DDL Hibernate would
  generate as the basis for a real migration tool (Flyway/Liquibase) — not introduced here to
  avoid changing this project's existing schema-management approach as a side effect of an auth
  change.
- If you introduce Redis for any reason, replace `DatabaseRateLimiter` with a Redis-backed
  `RateLimiter` — every call site already depends on the interface.
- Populate `security_blocklist` from whatever abuse signals you have (failed-login spikes,
  known-bad IP feeds, etc.) — there's no scheduled job or admin UI for it yet.
- Tighten `SecurityConfig.corsConfigurationSource()` to your real frontend origin(s) with
  `allowCredentials(true)` if you later move refresh tokens into an `httpOnly` cookie (see
  [Token strategy](#token-strategy)).
