# RideLink – Account Service

Owns passenger/driver/admin identity: registration, authentication (JWT issuance), role
management, profile self-service and account status management.

Part of the RideLink microservices system (IT3130 Application Development group assignment).
This service is developed and owned independently, with its own PostgreSQL database — no other
service may read or write this service's tables directly.

## Prerequisites

- Java 17+
- Maven 3.9+
- PostgreSQL 14+ (a local instance, or one per teammate/environment)

## Configuration

All configuration is via environment variables (see `src/main/resources/application.properties`
for defaults). Nothing sensitive is committed to the repository.

| Variable | Purpose | Example |
|---|---|---|
| `DB_URL` | JDBC URL for this service's own database | `jdbc:postgresql://localhost:5432/ridelink_account` |
| `DB_USERNAME` | Database user | `postgres` |
| `DB_PASSWORD` | Database password | `postgres` |
| `RIDELINK_JWT_SECRET` | **Required** - HMAC signing secret, no default. The app will not start without it. **Must be identical across all four RideLink services** so they can verify each other's tokens without calling back to this service. | a long random string, 32+ bytes |
| `RIDELINK_JWT_EXPIRATION_MINUTES` | Access token lifetime | `60` |
| `SERVER_PORT` | HTTP port | `8081` |

Create the database first:

```sql
CREATE DATABASE ridelink_account;
```

## Running locally

```bash
export DB_URL=jdbc:postgresql://localhost:5432/ridelink_account
export DB_USERNAME=postgres
export DB_PASSWORD=postgres
export RIDELINK_JWT_SECRET=replace-with-a-long-random-secret

mvn spring-boot:run
```

The service starts on `http://localhost:8081` (override with `SERVER_PORT`).

## API documentation

- Swagger UI: `http://localhost:8081/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8081/v3/api-docs`

## Running tests

```bash
mvn test
```

Tests run against an in-memory H2 database (`src/test/resources/application-test.properties`) —
no PostgreSQL instance is required to run the test suite. Covers:

- `JwtServiceTest` — token issuance/validation (unit)
- `AccountServiceTest` — registration and login business rules, including negative cases:
  duplicate email, wrong password, unknown email, suspended account (unit, Mockito)
- `AccountControllerIntegrationTest` — full register → login → authenticated `/me` flow,
  plus negative scenarios: duplicate email, missing token, wrong password (MockMvc)

## Endpoints

| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | `/api/accounts/register` | Public | Register a passenger or driver |
| POST | `/api/accounts/login` | Public | Authenticate, receive a JWT |
| GET | `/api/accounts/me` | Any authenticated user | View own profile |
| PUT | `/api/accounts/me` | Any authenticated user | Update own profile |
| GET | `/api/accounts/{id}` | Owner or admin only | Fetch account info by id |
| GET | `/api/accounts?role=` | Admin | List accounts, optional role filter |
| PATCH | `/api/accounts/{id}/status` | Admin | Suspend / reactivate / deactivate an account |

Admin accounts cannot be self-registered via `/register` — seed one directly in the database
for demo/testing purposes.

## How other services use this service

Account Service is the **only** service that issues JWTs. The other three RideLink services
(Driver & Vehicle, Ride Management, Fare & Payment) verify the JWT **locally**, using the same
`RIDELINK_JWT_SECRET`, without calling back to Account Service on every request. This keeps the
services loosely coupled and avoids Account Service becoming a bottleneck / single point of
failure for every authenticated call in the system.

**`GET /api/accounts/{id}` is restricted to the account owner or an admin** - it is not open to
any authenticated caller, because it returns personal details (email, phone number). This means
another RideLink service cannot currently call it using an ordinary passenger/driver token; it
would need to call it using an **admin-level token**, since there is no separate service-to-service
authentication mechanism implemented yet.

If a teammate's service needs to look up account details, the options are (pick one and document
it in the report's communication-interface section, since this is exactly the kind of choice
the assignment asks you to justify):
- Call this endpoint using an admin credential/token issued by this service (simplest, but couples
  that service to holding an admin login)
- Add a dedicated internal/service-role (e.g. a `SERVICE` role, or a separate static API key
  checked in a filter) that only the other three services use for machine-to-machine calls
- Have Account Service publish only the non-sensitive fields (id, role, status) on a separate,
  more open endpoint, keeping the full-detail endpoint owner/admin-only

None of these is implemented yet beyond the admin-token option above - this is a design decision
for the group to make and justify together.

## Sample test data

| Email | Password | Role |
|---|---|---|
| `passenger@ridelink.test` | `password123` | PASSENGER |
| `driver@ridelink.test` | `password123` | DRIVER |

(Create these via `POST /api/accounts/register`; there is no seed script by default.)
