# Auth quickstart — create a user, get a token, call protected endpoints

Assumes the app is running with the default port (`http://localhost:8080`) — swap the base URL if you changed `SERVER_PORT`. Full endpoint list: [README.md](README.md#api-reference).

> **Windows/PowerShell note**: PowerShell aliases `curl` to `Invoke-WebRequest`, which doesn't understand `-X`/`-d` the same way. Either run these from the Bash tool / Git Bash, or prefix the command with `curl.exe` instead of `curl` in a plain PowerShell window, or just use Swagger UI (steps below work there too).

## 1. Register a sample user

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"name":"Sample User","email":"sample@example.com","phone":"9000000001","password":"secret123"}'
```

Response:

```json
{
  "data": {
    "accessToken": "eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiIxIiwibmFtZSI6IlNhbXBsZSBVc2VyIi...",
    "tokenType": "Bearer",
    "expiresInMs": 86400000,
    "user": { "id": 1, "name": "Sample User", "email": "sample@example.com", "phone": "9000000001", "role": "CUSTOMER", ... }
  },
  "message": "Registered successfully",
  "success": true
}
```

Copy the `accessToken` value — that's your JWT. Already have a user? Get a fresh token instead via login:

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"emailOrPhone":"sample@example.com","password":"secret123"}'
```

(`emailOrPhone` accepts either the email or the phone number.)

## 2. Use the token

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

## 3. Getting a token for a different role

Every new account starts as `CUSTOMER`. `STORE_OWNER` and `DELIVERY` are granted by specific actions — **and since the JWT is stateless, you must log in again afterward to receive a token with the new role**:

- **Become a restaurant owner**: `POST /api/v1/store-owner/restaurants` with your current (customer) token → grants `STORE_OWNER` → log in again to get an owner-role token.
- **Become a rider**: `POST /api/v1/users/me/rider-profile` with your current token → grants `DELIVERY` → log in again to get a rider-role token.

```bash
# grant the role (still using the CUSTOMER token from step 1)
curl -s -X POST http://localhost:8080/api/v1/store-owner/restaurants \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"Pizza Palace","contactNumber":"1234567890","openingTime":"09:00:00","closingTime":"22:00:00","address":"123 Main St","latitude":"12.9716","longitude":"77.5946","isPureveg":false,"deliveryCharges":20,"deliveryRadius":5,"minOrderPrice":100,"isAcceptCod":true}'

# re-login to pick up the new role
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"emailOrPhone":"sample@example.com","password":"secret123"}' \
  | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)
```

A quick way to confirm which role is in your current token without a JWT decoder: the `role` field is also echoed back in the `user` object of every register/login response.

## 4. Token expiry

Tokens last `JWT_EXPIRATION_MS` (default 24h — see [Configuration reference](README.md#configuration-reference)). Once expired, protected endpoints return `401 Unauthorized` ("Authentication required") — just log in again for a new token.
