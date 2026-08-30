# Auth quickstart — create a user, get a token, call protected endpoints

Assumes the app is running with the default port (`http://localhost:8080`) — swap the base URL if you changed `SERVER_PORT`. Full endpoint list: [README.md](README.md#api-reference). Full architecture/flow reference: [AUTH_SECURITY.md](AUTH_SECURITY.md) and [pureeats-user-service/README.md](pureeats-user-service/README.md).

> **Windows/PowerShell note**: PowerShell aliases `curl` to `Invoke-WebRequest`, which doesn't understand `-X`/`-d` the same way. Either run these from the Bash tool / Git Bash, or prefix the command with `curl.exe` instead of `curl` in a plain PowerShell window, or just use Swagger UI (steps below work there too).

There is no password login. Every account — including `SUPER_ADMIN`/`ADMIN` — authenticates by
proving ownership of an email or phone number via a one-time code.

## 1. Sign up (email)

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"fullName":"Sample User","email":"sample@example.com"}'
```

Response:

```json
{
  "success": true,
  "data": {
    "success": true,
    "message": "OTP sent successfully.",
    "challengeId": "886998e2-b2c6-4589-9619-28fe4effdfd5",
    "maskedDestination": "s****e@example.com",
    "expiresIn": 600,
    "resendAvailableIn": 30
  }
}
```

By default no real email is sent (`notification.email-provider=console`) — the OTP is printed to
the **server console/log**, not returned in this response. Look for a line like:

```
[DEV EMAIL] to=s****e@example.com subject=Verify your PureEats account
Hello there, Your PureEats verification code is: 123456 ...
```

Already have an account (phone or email)? Start a login challenge instead:

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/otp/send \
  -H "Content-Type: application/json" \
  -d '{"method":"EMAIL","email":"sample@example.com"}'
```

or for phone:

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/otp/send \
  -H "Content-Type: application/json" \
  -d '{"method":"PHONE","countryId":1,"phone":"9000000001"}'
```

A phone/email that's never been seen before still gets a challenge, and the account is created
silently on first successful verification — same call, no separate "register" step needed for
phone signup.

## 2. Verify the OTP → get a token

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/otp/verify \
  -H "Content-Type: application/json" \
  -d '{"challengeId":"886998e2-b2c6-4589-9619-28fe4effdfd5","otp":"123456"}'
```

Response:

```json
{
  "success": true,
  "message": "Authentication successful.",
  "data": {
    "accessToken": "eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiIxIiwibmFtZSI6IlNhbXBsZSBVc2VyIi...",
    "refreshToken": "Yt3qX9f0P2m1...",
    "tokenType": "Bearer",
    "expiresIn": 900
  }
}
```

Copy the `accessToken` value — that's your JWT (short-lived, 15 minutes by default). Copy
`refreshToken` too — see step 5.

## 3. Use the token

### In Swagger UI
1. Open `http://localhost:8080/swagger-ui/index.html`
2. Click the **Authorize** button (top right, padlock icon)
3. Paste **just the token value** (no `Bearer ` prefix — Swagger adds that for you) into the `bearerAuth` field
4. Click **Authorize**, then **Close**
5. Every 🔒 endpoint's "Try it out" now sends the token automatically

### With curl
Add an `Authorization: Bearer <token>` header to any request. Save the token to a shell variable first so you don't have to paste the whole thing each time:

```bash
TOKEN="eyJhbGciOiJIUzM4NCJ9...."   # paste your accessToken here

curl -s http://localhost:8080/api/v1/users/me \
  -H "Authorization: Bearer $TOKEN"
```

Example: save an address, then place an order using it:

```bash
curl -s -X POST http://localhost:8080/api/v1/users/me/addresses \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"house":"Flat 4B","address":"MG Road","landmark":"Near Mall","tag":"Home","latitude":"12.9720","longitude":"77.5950","makeDefault":true}'
```

## 4. Getting a token for a different role

Every new self-registered account starts as `CUSTOMER`. `STORE_OWNER` and `DELIVERY` are granted by specific actions — **and since the JWT is stateless, you must log in again (a fresh OTP challenge + verify) afterward to receive a token with the new role**:

- **Become a restaurant owner**: `POST /api/v1/store-owner/restaurants` with your current (customer) token → grants `STORE_OWNER` → verify a fresh OTP to get an owner-role token.
- **Become a rider**: `POST /api/v1/users/me/rider-profile` with your current token → grants `DELIVERY` → verify a fresh OTP to get a rider-role token.

```bash
# grant the role (still using the CUSTOMER token from step 2)
curl -s -X POST http://localhost:8080/api/v1/store-owner/restaurants \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"Pizza Palace","contactNumber":"1234567890","openingTime":"09:00:00","closingTime":"22:00:00","address":"123 Main St","latitude":"12.9716","longitude":"77.5946","isPureveg":false,"deliveryCharges":20,"deliveryRadius":5,"minOrderPrice":100,"isAcceptCod":true}'

# start a fresh login challenge, then verify it, to pick up the new role
curl -s -X POST http://localhost:8080/api/v1/auth/otp/send \
  -H "Content-Type: application/json" -d '{"method":"EMAIL","email":"sample@example.com"}'
# ... verify with the new challengeId + OTP from the console log, as in step 2 ...
```

`SUPER_ADMIN`/`ADMIN` are never granted this way — see
[AUTH_SECURITY.md → Privileged roles](AUTH_SECURITY.md) for how those are provisioned.

A quick way to confirm which role is in your current token without a JWT decoder: decode the
JWT's payload (e.g. paste it into [jwt.io](https://jwt.io) or `echo '<payload-part>' | base64 -d`) — the `role` claim is right there.

## 5. Refreshing an access token

Access tokens are deliberately short-lived (15 minutes). Instead of a full OTP re-verification,
exchange the refresh token you got in step 2:

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"Yt3qX9f0P2m1..."}'
```

This returns a **new** access token *and* a **new** refresh token — the old refresh token is dead
the instant this call succeeds (rotation). Save the new one for the next refresh.

## 6. Logging out

```bash
# revoke just this session
curl -s -X POST http://localhost:8080/api/v1/auth/logout \
  -H "Content-Type: application/json" -d '{"refreshToken":"'"$REFRESH_TOKEN"'"}'

# revoke every session for this account (needs the access token, not the refresh token)
curl -s -X POST http://localhost:8080/api/v1/auth/logout-all \
  -H "Authorization: Bearer $TOKEN"
```
