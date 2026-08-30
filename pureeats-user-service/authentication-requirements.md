# Authentication, OTP Verification, Security & User Activity Enhancement

## Objective

Enhance the existing Spring Boot application by implementing a production-ready, reusable and extensible authentication and security subsystem.

The implementation must follow:

* SOLID principles
* Clean Architecture / layered architecture appropriate for the existing project
* Reusable components
* Separation of concerns
* Proper DTO/request/response models
* Secure handling of OTPs, tokens and personally identifiable information
* Database normalization and appropriate indexing
* Transactional integrity
* Testability
* Easy extension to additional authentication/notification providers in the future

Do not implement this as a collection of tightly coupled controller/service methods. Design reusable abstractions that can support additional authentication methods and notification providers later.

---

# 1. Authentication Flow

Implement OTP-based authentication for both:

1. Phone number
2. Email address

The authentication flow should be:

```text
Client
   |
   | POST /auth/login
   v
Create Authentication Challenge
   |
   +-- Generate OTP
   |
   +-- Persist challenge securely
   |
   +-- Send OTP through Notification Service
   |
   v
Return challenge_id/message_uuid
   |
   | POST /auth/verify
   | challenge_id + otp
   v
Validate challenge
   |
   +-- Validate expiry
   +-- Validate attempt count
   +-- Validate lock status
   +-- Validate OTP
   |
   v
Successful authentication
   |
   +-- Mark email/phone as verified
   +-- Create authenticated session
   +-- Generate access token
   +-- Generate refresh token
   |
   v
Return authentication response
```

The client should not need to send the email/phone again during OTP verification if a secure `challenge_id` is available.

---

# 2. Login API

Create:

```http
POST /auth/login
```

Support both phone and email authentication.

## Preferred phone request

```json
{
  "method": "PHONE",
  "country_id": 1,
  "phone": "9876543210"
}
```

## Preferred email request

```json
{
  "method": "EMAIL",
  "email": "john@gmail.com"
}
```

Avoid unnecessary fields such as:

```text
hash
csrf_token
lc
theme
id_token
fb_token
name
otp
```

unless they are actually required by the application's security architecture.

The backend should validate the request based on the selected authentication method.

For example:

* `PHONE` requires `country_id + phone`
* `EMAIL` requires `email`

Use enums rather than arbitrary strings where appropriate.

Example:

```java
public enum AuthenticationMethod {
    PHONE,
    EMAIL
}
```

---

# 3. Login Response

Return an opaque authentication challenge identifier.

Example:

```json
{
  "success": true,
  "message": "OTP sent successfully.",
  "challenge_id": "886998e2-b2c6-4589-9619-28fe4effdfd5",
  "expires_in": 600,
  "resend_available_in": 30
}
```

`challenge_id` should not expose sensitive information.

Do not expose the OTP itself.

For privacy, mask email/phone in the response when appropriate.

Example:

```json
{
  "success": true,
  "message": "OTP sent successfully.",
  "masked_destination": "j*****n@gmail.com",
  "challenge_id": "...",
  "expires_in": 600
}
```

---

# 4. OTP Generation

Create a reusable OTP generation component.

Example abstraction:

```java
public interface OtpGenerator {
    String generate();
}
```

The implementation should:

* Generate cryptographically secure random OTPs
* Support configurable OTP length
* Never log OTP values
* Never return OTP values in API responses
* Store only a secure representation of the OTP if practical
* Support configurable expiry

Default:

```text
OTP length: 6 digits
OTP validity: 10 minutes
```

All values must be configurable through application configuration.

---

# 5. OTP Challenge

Create a reusable OTP challenge concept instead of coupling OTP logic directly to users.

Suggested information:

```text
challenge_id
user_id
authentication_method
destination
otp_hash
status
expires_at
attempt_count
max_attempts
resend_count
max_resend_count
last_sent_at
created_at
verified_at
ip_address
device_id
```

Possible status:

```java
public enum OtpChallengeStatus {
    PENDING,
    VERIFIED,
    EXPIRED,
    LOCKED,
    CANCELLED
}
```

OTP verification must operate against the challenge.

---

# 6. OTP Verification

Create:

```http
POST /auth/verify
```

Preferred request:

```json
{
  "challenge_id": "886998e2-b2c6-4589-9619-28fe4effdfd5",
  "otp": "239843"
}
```

Successful response:

```json
{
  "success": true,
  "message": "Authentication successful.",
  "access_token": "...",
  "refresh_token": "...",
  "token_type": "Bearer",
  "expires_in": 900
}
```

Invalid OTP:

```json
{
  "success": false,
  "message": "The OTP entered is invalid or incorrect.",
  "error_code": "INVALID_OTP",
  "attempts_remaining": 2
}
```

If no attempts remain:

```json
{
  "success": false,
  "message": "Too many incorrect attempts. Please request a new OTP.",
  "error_code": "OTP_ATTEMPTS_EXCEEDED"
}
```

Do not reveal whether an OTP exists or whether a specific account exists in situations where that would create account-enumeration risk.

---

# 7. OTP Retry / Attempt Policy

Implement configurable security policies:

```text
OTP validity = 10 minutes
Maximum verification attempts = configurable
Maximum resend attempts = configurable
Resend cooldown = configurable
Maximum OTP requests per user = configurable
Maximum OTP requests per IP = configurable
```

Example defaults:

```text
OTP expiry: 10 minutes
Verification attempts: 5
Resend cooldown: 30 seconds
Maximum resend requests: 3
```

When the maximum number of verification attempts is exceeded:

```text
Challenge -> LOCKED
```

A new OTP challenge should be required.

Implement this in a reusable policy/service rather than hardcoding values in controllers.

---

# 8. Resend OTP

Create:

```http
POST /auth/resend
```

Preferred request:

```json
{
  "challenge_id": "886998e2-b2c6-4589-9619-28fe4effdfd5"
}
```

The server should:

1. Validate challenge
2. Validate cooldown
3. Validate maximum resend count
4. Generate a new OTP
5. Invalidate the previous OTP
6. Store the new OTP securely
7. Send notification
8. Update challenge information
9. Return the same or a new challenge ID based on the chosen design

Response:

```json
{
  "success": true,
  "message": "A new OTP has been sent.",
  "expires_in": 600,
  "resend_available_in": 30
}
```

Do not allow unlimited resend requests.

---

# 9. Account Locking

Implement configurable temporary account locking.

Lock conditions may include:

* Excessive invalid OTP attempts
* Excessive login attempts
* Suspicious activity
* Repeated requests from blocked IP/device
* Security rules

Suggested account status:

```java
public enum AccountStatus {
    ACTIVE,
    TEMPORARILY_LOCKED,
    BLOCKED,
    DISABLED
}
```

Temporary locks should have:

```text
locked_at
locked_until
lock_reason
```

Do not permanently lock accounts merely because of a few incorrect OTP attempts.

---

# 10. IP / Device / Email / Phone Blocklist

Implement a reusable blocklist/security-rule mechanism.

It should support blocking based on:

```text
IP address
Device ID
Email
Phone number
User ID
```

Suggested structure:

```text
security_blocklist
------------------
id
block_type
value
reason
status
created_at
expires_at
created_by
```

Example:

```java
public enum BlockType {
    IP,
    DEVICE,
    EMAIL,
    PHONE,
    USER
}
```

The implementation should allow future block types.

Support:

* Temporary blocks
* Permanent blocks
* Expiration
* Reason
* Audit information

Check the blocklist before sensitive authentication operations.

---

# 11. Rate Limiting

Implement rate limiting for authentication endpoints.

At minimum:

```text
/auth/login
/auth/verify
/auth/resend
```

Rate limits should consider:

```text
IP
User/account
Phone
Email
Device
```

Avoid relying only on one dimension.

The implementation should be designed so that the rate-limiting mechanism can later be backed by Redis or another distributed store.

If the existing application already uses Redis, prefer a Redis-based implementation for distributed deployments.

---

# 12. Notification Service

Create a reusable notification abstraction.

The authentication module must not directly contain SMS or email implementation details.

Suggested abstraction:

```java
public interface NotificationService {

    void send(NotificationRequest request);
}
```

Possible implementations:

```text
EmailNotificationService
SmsNotificationService
```

Or provider-specific implementations:

```text
GmailEmailProvider
TwilioSmsProvider
AwsSesEmailProvider
```

The application should depend on interfaces rather than concrete providers.

---

# 13. Notification Types

Design notifications using reusable templates.

Examples:

```java
public enum NotificationType {
    LOGIN_OTP,
    SIGNUP_OTP,
    PASSWORD_RESET_OTP,
    EMAIL_VERIFICATION,
    PHONE_VERIFICATION
}
```

This should allow additional notification types to be added without modifying the authentication business logic.

---

# 14. Email Notification

Implement configurable email sending.

The notification service should support:

1. Plain text emails
2. HTML emails
3. Dynamic parameters

Example:

```text
Template:
login-otp.html
```

Parameters:

```json
{
  "otp": "123456",
  "expiryMinutes": 10,
  "userName": "John"
}
```

The template engine should resolve the parameters dynamically.

Example:

```html
Hello {{userName}},

Your OTP is {{otp}}.

This OTP is valid for {{expiryMinutes}} minutes.
```

The implementation should be flexible enough to support different template engines if required.

---

# 15. Gmail Configuration

Provide a complete README explaining how to configure Gmail SMTP.

Document:

```text
SMTP host
SMTP port
SMTP username
SMTP password
TLS configuration
Authentication
Application password
```

Do not recommend storing a Gmail account's normal password directly in source code.

Explain the recommended Gmail App Password approach where applicable.

Configuration must be externalized through:

```yaml
spring:
  mail:
    host:
    port:
    username:
    password:
    properties:
      mail:
        smtp:
          auth:
          starttls:
            enable:
```

Sensitive configuration must be provided through environment variables or secrets.

Example:

```yaml
username: ${MAIL_USERNAME}
password: ${MAIL_PASSWORD}
```

---

# 16. SMS Notification

Create the same abstraction for SMS.

The authentication service should not know which SMS provider is being used.

For example:

```java
public interface SmsProvider {
    void send(String phoneNumber, String message);
}
```

The provider can later be replaced without modifying authentication logic.

---

# 17. Notification Template Architecture

Create a reusable notification template system supporting:

```text
notification type
channel
template name
subject
body
language
dynamic parameters
```

Potential structure:

```text
templates/
    email/
        login-otp.html
        login-otp.txt
        signup-otp.html
    sms/
        login-otp.txt
```

Support both HTML and text versions.

---

# 18. User Verification Status

Track verification status independently for:

```text
Email
Phone
```

Do not infer verification status merely from the presence of an email/phone.

Suggested fields:

```text
email
email_verified
email_verified_at

phone
phone_verified
phone_verified_at
```

If the user successfully authenticates using an email OTP:

```text
email_verified = true
email_verified_at = current timestamp
```

If the user successfully authenticates using phone OTP:

```text
phone_verified = true
phone_verified_at = current timestamp
```

If the application is expected to support multiple emails/phone numbers per user in the future, consider separate normalized tables instead of keeping everything directly in the user table.

---

# 19. Signup

Create:

```http
POST /auth/signup
```

Example:

```json
{
  "full_name": "John Doe",
  "email": "john@gmail.com"
}
```

If the email already exists:

```json
{
  "success": false,
  "message": "Email ID already registered.",
  "error_code": "EMAIL_ALREADY_REGISTERED"
}
```

Signup should also use OTP verification if email/phone verification is required.

Do not automatically mark email/phone as verified merely because it was supplied during signup.

---

# 20. User Activity / Audit Tracking

Implement a reusable user activity/audit system.

Track authentication and meaningful security-related activities.

Examples:

```text
LOGIN_INITIATED
OTP_SENT
OTP_VERIFICATION_SUCCESS
OTP_VERIFICATION_FAILED
OTP_RESENT
LOGIN_SUCCESS
LOGIN_FAILED
LOGOUT
ACCOUNT_LOCKED
ACCOUNT_UNLOCKED
ACCOUNT_BLOCKED
ACCOUNT_UNBLOCKED
EMAIL_VERIFIED
PHONE_VERIFIED
TOKEN_REFRESHED
TOKEN_REVOKED
```

---

# 21. Request Metadata

For relevant requests, capture:

```text
user_id
IP address
user agent
device information
browser
operating system
device type
request timestamp
API endpoint
HTTP method
request ID / correlation ID
authentication result
failure reason
```

Avoid storing sensitive information unnecessarily.

Never store:

```text
raw OTP
password
access token
refresh token
```

in application logs.

If tokens need to be persisted for session management, store secure hashes/references rather than raw secrets where appropriate.

---

# 22. IP Geolocation

Resolve approximate location from the request IP using a free/available IP geolocation API.

Capture useful information such as:

```text
country
country_code
region/state
city
latitude
longitude
timezone
ISP/organization where available
```

The geolocation provider must be abstracted:

```java
public interface IpGeolocationService {
    GeoLocation resolve(String ipAddress);
}
```

Do not make authentication fail if the external geolocation API is unavailable.

Use:

```text
timeout
fallback
error handling
caching
```

where appropriate.

The geolocation API should be configurable so it can later be replaced.

Do not treat IP geolocation as an exact physical location. Store it as approximate location information.

---

# 23. Device Tracking

Create a device/session model.

Capture meaningful device information such as:

```text
device_id
device_type
browser
browser_version
operating_system
OS version
user_agent
IP
first_seen_at
last_seen_at
last_login_at
```

Where appropriate, allow the React application to provide a stable device identifier.

Do not rely solely on User-Agent as a unique device identifier.

Consider a separate:

```text
user_device
```

table.

---

# 24. Login History

Create a separate login history/audit table rather than putting all login information in the user table.

Suggested fields:

```text
id
user_id
login_method
status
ip_address
device_id
user_agent
country
region
city
latitude
longitude
timestamp
failure_reason
```

This will make security auditing and reporting easier.

---

# 25. Database Design

Prefer separate tables where the data represents an independent concept.

Potential tables:

```text
users
user_devices
otp_challenges
login_history
security_blocklist
user_sessions
notification_logs
audit_logs
```

Do not create unnecessary tables if an existing project entity already represents the same concept.

Use:

* Foreign keys
* Unique constraints
* Appropriate indexes
* Created/updated timestamps
* Proper enum handling
* Optimistic locking where useful
* Database-level constraints for critical uniqueness requirements

Consider indexing:

```text
users.email
users.phone
otp_challenges.challenge_id
otp_challenges.user_id
otp_challenges.expires_at
user_devices.device_id
login_history.user_id
login_history.ip_address
security_blocklist.type + value
user_sessions.user_id
```

Use a UUID/ULID strategy for externally exposed identifiers where appropriate.

---

# 26. Refresh Token Design

Implement access and refresh tokens if the application requires persistent React sessions.

Recommended approach:

```text
Access Token
    |
    +-- Short lifetime: 10–15 minutes

Refresh Token
    |
    +-- Longer lifetime
    +-- Used to obtain a new access token
    +-- Revocable
    +-- Associated with a user session/device
```

Create:

```http
POST /auth/refresh
```

Example:

```json
{
  "refresh_token": "..."
}
```

The refresh-token system should support:

* Token expiration
* Token rotation
* Token revocation
* Logout
* Device/session revocation
* Suspicious-session invalidation

Do not require OTP for every access-token renewal.

For a browser-based React application, carefully consider secure cookie-based refresh tokens rather than exposing long-lived refresh tokens to JavaScript/localStorage.

Document the chosen approach and explain the security trade-offs.

---

# 27. Logout

Implement:

```http
POST /auth/logout
```

Logout should revoke the current authenticated session/refresh token.

Also consider:

```http
POST /auth/logout-all
```

to revoke all sessions for a user.

This is useful when a user suspects their account has been compromised.

---

# 28. Session Management

Maintain server-side session information for refresh-token-based authentication.

Suggested information:

```text
session_id
user_id
device_id
refresh_token_hash
created_at
expires_at
last_used_at
revoked_at
IP address
user_agent
```

This enables:

```text
View logged-in devices
Revoke a device
Logout from all devices
Detect suspicious sessions
```

---

# 29. Security Event Architecture

Create a reusable security/audit event model.

Example:

```java
public interface SecurityEventPublisher {
    void publish(SecurityEvent event);
}
```

This allows the application to later integrate:

```text
Kafka
AWS SNS
CloudWatch
SIEM
notification systems
```

without coupling authentication to those systems.

---

# 30. API Error Handling

Create a consistent API error response.

Example:

```json
{
  "success": false,
  "error_code": "OTP_EXPIRED",
  "message": "The OTP has expired. Please request a new OTP.",
  "request_id": "..."
}
```

Use centralized exception handling:

```java
@RestControllerAdvice
```

Do not expose stack traces or internal implementation details to clients.

---

# 31. Correlation / Request ID

Every request should have a correlation/request ID.

Example:

```text
X-Request-ID
```

If the client does not provide one, generate it.

Include it in:

* Application logs
* Audit records where useful
* Error responses
* Notification logs where useful

This will make troubleshooting significantly easier.

---

# 32. Logging and PII Protection

Implement structured logging.

Never log:

```text
OTP
password
access token
refresh token
full authentication secrets
```

Mask sensitive information:

```text
john@gmail.com
=> j***n@gmail.com

9876543210
=> ******3210
```

IP address and other sensitive data should be handled according to the application's privacy requirements.

---

# 33. SOLID / Reusable Architecture

Avoid a large:

```java
AuthService
```

containing all authentication, OTP, notification, token, geolocation, device and audit logic.

Prefer smaller responsibilities such as:

```text
AuthenticationService
OtpService
OtpChallengeService
OtpPolicyService
NotificationService
EmailNotificationService
SmsNotificationService
TokenService
RefreshTokenService
SessionService
DeviceService
IpGeolocationService
RateLimitService
BlocklistService
AuditService
SecurityEventService
```

Use interfaces at provider/infrastructure boundaries.

For example:

```java
OtpGenerator
OtpStore
NotificationService
EmailProvider
SmsProvider
TokenService
IpGeolocationService
RateLimitService
```

The domain/application layer should not be tightly coupled to external providers.

---

# 34. Suggested Package Structure

Adapt this to the existing application's package structure rather than blindly creating duplicate layers.

Possible structure:

```text
auth/
    controller/
    dto/
    service/
    domain/
    repository/
    security/
    exception/

notification/
    service/
    provider/
    template/
    dto/
    exception/

security/
    blocklist/
    ratelimit/
    audit/
    device/
    session/
    geolocation/

user/
    entity/
    repository/
    service/
```

Keep infrastructure/provider-specific code separated from business logic.

---

# 35. Configuration

All security-sensitive and operational values must be configurable.

For example:

```yaml
security:
  otp:
    length: 6
    expiry-minutes: 10
    max-attempts: 5
    resend-cooldown-seconds: 30
    max-resends: 3

  session:
    access-token-expiry-minutes: 15
    refresh-token-expiry-days: 30

  rate-limit:
    login:
    verify:
    resend:

  geolocation:
    provider:
    timeout:

notification:
  email:
    provider:
  sms:
    provider:
```

Do not hardcode these values in Java classes.

---

# 36. Concurrency and Security

OTP verification and retry counting must be safe under concurrent requests.

Prevent race conditions such as:

```text
Two simultaneous OTP verification requests
Two simultaneous resend requests
Multiple login requests bypassing rate limits
```

Use appropriate:

* Database locking
* Atomic updates
* Redis atomic operations where applicable
* Transaction boundaries

Do not rely only on Java in-memory counters because the application may run multiple instances.

---

# 37. Notification Logging

Track notification delivery attempts.

Suggested fields:

```text
id
notification_type
channel
destination_masked
provider
status
provider_message_id
failure_reason
created_at
sent_at
```

Do not store OTP values in notification logs.

This will make it possible to investigate:

```text
OTP generated but not sent
OTP provider failure
Email delivery failure
SMS provider failure
```

---

# 38. Testing

Add comprehensive tests.

## Unit tests

Cover:

```text
OTP generation
OTP expiry
OTP verification
wrong OTP
attempt limits
resend limits
resend cooldown
account locking
blocklist
rate limiting
email masking
phone masking
token generation
refresh token rotation
session revocation
```

## Integration tests

Cover complete flows:

```text
Phone login -> OTP -> verify -> access token
Email login -> OTP -> verify -> access token
Invalid OTP
Expired OTP
Resend OTP
Maximum attempts
Blocked account
Blocked IP
Blocked device
Refresh token
Logout
Logout all
```

Use appropriate test infrastructure for external providers.

Do not send real SMS/email during automated tests.

---

# 39. API Documentation

Document all APIs using OpenAPI/Swagger.

At minimum:

```text
POST /auth/signup
POST /auth/login
POST /auth/verify
POST /auth/resend
POST /auth/refresh
POST /auth/logout
POST /auth/logout-all
```

Document:

* Request
* Response
* Validation
* Error codes
* Authentication requirements
* Rate limits where useful

---

# 40. README Documentation

Create/update README documentation explaining:

1. Authentication architecture
2. OTP flow
3. Email configuration
4. Gmail configuration
5. SMS provider configuration
6. Environment variables
7. Database tables
8. Refresh-token strategy
9. Security configuration
10. Rate limits
11. Blocklist behavior
12. Local development setup
13. Testing
14. Production recommendations

Include example requests and responses.

---

# 41. Important Implementation Rules

Before writing code:

1. Inspect the existing project structure.
2. Identify existing User/Customer entities.
3. Identify existing authentication/security implementation.
4. Identify existing notification code.
5. Identify existing database entities and relationships.
6. Identify existing Redis/cache infrastructure if present.
7. Reuse existing abstractions where they are good.
8. Do not duplicate existing functionality.
9. Do not unnecessarily rename existing APIs/entities.
10. Maintain backward compatibility where practical.
11. Do not introduce a new library if an existing dependency already provides the required capability.
12. Use the project's existing Java/Spring Boot version and conventions.
13. Do not expose sensitive information through logs or API responses.

---

# 42. Expected Final Deliverables

Implement the complete feature and provide:

### Backend

* Authentication APIs
* OTP generation/verification
* OTP retry/lock policy
* Resend OTP
* Email OTP
* SMS OTP abstraction
* Signup verification
* Access token
* Refresh token
* Session management
* Logout
* Blocklist
* Rate limiting
* Device tracking
* IP tracking
* IP geolocation
* Login history
* Audit/security events
* Email verification status
* Phone verification status
* Notification service
* HTML/text email templates
* Gmail configuration

### Database

Create/update the required entities and migrations.

Prefer separate tables for:

```text
OTP challenges
Devices
Login history
Sessions
Blocklist
Audit logs
Notification logs
```

where justified by the existing design.

### Documentation

Provide:

```text
README
API documentation
Configuration documentation
Gmail setup instructions
Environment variable documentation
Authentication flow documentation
Database design explanation
Security considerations
```

### Testing

Provide unit and integration tests for all important authentication and security scenarios.

---

# 43. Final Quality Requirements

The final implementation should be production-oriented rather than a basic demo implementation.

Pay particular attention to:

* Security
* SOLID principles
* Separation of concerns
* Reusability
* Extensibility
* Database design
* Concurrency
* Rate limiting
* Privacy/PII protection
* Error handling
* Testability
* Maintainability
* Documentation

Before completing the implementation, review the code for:

```text
SOLID violations
duplicate logic
hardcoded configuration
security vulnerabilities
PII leakage
race conditions
unnecessary database queries
missing indexes
missing transaction boundaries
poor exception handling
tight coupling to notification providers
tight coupling to geolocation providers
```

Also provide a short implementation summary at the end explaining:

1. What was changed
2. New/modified entities
3. New APIs
4. Authentication flow
5. Token strategy
6. Notification architecture
7. Security mechanisms
8. Configuration required
9. Database migration requirements
10. How to run and test the implementation
