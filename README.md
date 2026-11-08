# Branch Appointments Backend

Appointment booking for a single bank branch: customers book a service in an available slot, branch staff run the
day, and the branch manager manages the branch, the service catalog and the staff. This repository is the backend —
a Spring Boot modular monolith serving a versioned REST API at `/api/v1`.

**Stack:** Java 21, Spring Boot 4.1, Spring Modulith 2.1, jMolecules 2025.0, Maven, PostgreSQL + Flyway, Redis,
Keycloak, HashiCorp Vault, OpenTelemetry.

## Contents

- [Architecture](#architecture)
- [Prerequisites](#prerequisites)
- [Quick start](#quick-start)
- [Local services](#local-services)
- [Test users](#test-users)
- [Configuration](#configuration)
- [Secrets](#secrets)
- [Demo data](#demo-data)
- [Tests](#tests)
- [API conventions](#api-conventions)
- [Security](#security)
- [Notifications](#notifications)
- [Events and rate limits](#events-and-rate-limits)
- [Observability](#observability)
- [Container image](#container-image)
- [Continuous integration](#continuous-integration)
- [Related repositories](#related-repositories)
- [Working on the code](#working-on-the-code)
- [Further documentation](#further-documentation)

## Architecture

The base package is `com.samdev.appointments`. Each direct sub-package is one bounded context, verified by
Spring Modulith:

| Module | Responsibility |
| --- | --- |
| `branchmanagement` | The branch, its weekly hours, calendar exceptions, capacity pools, service catalog and offerings |
| `identity` | Token validation, roles, staff members and their invitations, rate limiting |
| `customer` | Customer records and the only store of personal data |
| `booking` | Appointments, the schedule projection and the only enforcement of capacity |
| `notification` | Email and SMS dispatch |
| `shared` | Web conventions only (`PageResponse`, `ProblemDetailsSupport`) — holds no data and depends on no module |

The application runs for **one branch**. Its code, name, address, location, time zone and customer segments are
configuration (`appointments.branch` in `application.yml`, overridable with `BRANCH_*` environment variables) and are
exposed as `BranchProfile` through `BranchDirectory.branch()`. Nothing stores or passes a branch ID. The weekly hours
are a single `OpeningHours` row, created by the migration and empty until the branch manager sets them.

```
src/main/java/com/samdev/appointments/
├── AppointmentsApplication.java      # entry point
├── RequiredSettings.java             # fails start-up on missing configuration
├── EventPublicationMaintenance*      # outbox retry and cleanup
├── branchmanagement/                 # each module: application/ domain/ infrastructure/ web/
├── identity/
├── customer/
├── booking/
├── notification/
└── shared/
```

## Prerequisites

- Java 21
- Maven
- Docker, running — required both for the local services and for the Testcontainers-based tests

## Quick start

```bash
# 1. Start PostgreSQL, Redis, Vault, Keycloak, Mailpit and the monitoring stack
docker compose up -d

# 2. Run the backend on port 8081 with the `local` profile and demo data
mvn spring-boot:run
```

`mvn spring-boot:run` uses the `local` profile by default (`spring-boot.run.profiles` in `pom.xml`). The backend will
not start without Vault, which `docker compose` provides.

To run against a throwaway Testcontainers database instead of the compose one:

```bash
mvn spring-boot:test-run
```

Availability works immediately after start-up, because `booking` builds its schedule projection then:

```bash
curl "http://localhost:8081/api/v1/availability?serviceId=&date="
```

## Local services

`compose.yaml` contains only what the backend needs during development.

| Service | URL / host port | Notes |
| --- | --- | --- |
| Backend | http://localhost:8081 | Started by `mvn spring-boot:run` |
| PostgreSQL 17 | `localhost:5433` | 5432 is often taken by a local install; override with `POSTGRES_PORT` |
| Redis | `localhost:6379` | Cache only — safe to flush |
| Keycloak | http://localhost:8180 | Admin console at `/admin`, credentials `admin` / `admin` |
| Vault (dev mode) | http://localhost:8200 | Root token `local-dev-root-token` |
| Mailpit | http://localhost:8025 | Inbox; SMTP on `localhost:1025` |
| Grafana | http://localhost:3000 | Dashboard "Appointments overview" |
| Frontend dev server | http://localhost:4200 | Separate repository; proxies `/api` to 8081 |

Reset everything, including the demo data:

```bash
docker compose down -v
```

## Test users

Sign-in goes through the Keycloak realm `appointments`, defined as code in `keycloak/appointments-realm.json` — the
same file for every environment, with values supplied via `${KC_...}` placeholders. Local users live in
`keycloak/local/` and all share the password `Local-dev-password-1`.

| User | Role |
| --- | --- |
| `manager@appointments.test` | Invited as the first branch manager at start-up |
| `staff@appointments.test` | Invite them as branch staff; they already have a login, so no setup email is sent |
| `customer@appointments.test` | Customer |

Admin requests need a second factor, so the first admin sign-in asks to set up an authenticator app.

The `appointments-web` client's redirect URI is `http://localhost:4200/*` (`KC_WEB_REDIRECT_URI` in `compose.yaml`),
so sign-in returns the user to the page they were on.

## Configuration

The default — no profile — is production. `application.yml` reads everything that differs between environments from
environment variables. A placeholder without a default (`${DATABASE_URL}`, `${FRONTEND_URL}`, `${BRANCH_NAME}`, ...)
must be set; `RequiredSettings`, an `EnvironmentPostProcessor`, stops start-up and lists every variable that is
missing.

Production defaults: Vault AppRole sign-in, Redis over TLS, SMTP on 587 with STARTTLS, 10% trace sampling, and no
OpenAPI or Swagger UI.

### Profiles

| Profile | Used by | Behaviour |
| --- | --- | --- |
| *(none)* | The jar and the container image | Production defaults |
| `local` | `mvn spring-boot:run`, `mvn spring-boot:test-run` | Points the same variable names at the compose services and the dev server on 4200, and loads the demo data |
| *(none)* | Tests | Variables come from `src/test/resources/application.properties`; the demo loader tests activate `local` |

A real environment variable always wins over the `local` profile's value.

Two things to know about profile resolution:

- Only the local Vault address and token sit in a `local` section at the end of `application.yml`, because Vault is
  read before the profile-specific files.
- Spring Modulith reads the configuration a second time from system properties and environment variables only, so the
  profile has to come from one of those — `SPRING_PROFILES_ACTIVE` or `-Dspring.profiles.active`, not just a
  `--spring.profiles.active` argument. `spring-boot:run` and `spring-boot:test-run` pass it as that system property
  for you.

## Secrets

Every backend secret comes from Vault and from nowhere else. At start-up the backend reads
`secret/appointments/backend` and `secret/appointments/shared` through Spring Cloud Vault, with a required
`spring.config.import: vault://...` and fail-fast enabled. Each key becomes a property referenced by a placeholder in
`application.yml` — for example `${database-password}`, and the database username too, since the login changes as one
unit. There are no defaults and no environment-variable fallbacks, so `mvn spring-boot:run` does not start without
Vault.

Locally a dev-mode Vault holds them. `vault/init.sh`, run by the `vault-init` compose service on every
`docker compose up`, seeds the local values. **Adding a secret means adding a key in `init.sh` and a placeholder in
`application.yml`.** Keycloak and postgres-exporter cannot read Vault, so `init.sh` renders their values into the
git-ignored `.secrets/` directory.

Tests and `spring-boot:test-run` run without Vault: `spring.config.on-not-found=ignore` (a surefire system property,
and `TestAppointmentsApplication`) skips the import, and their secrets live in
`src/test/resources/application.properties`.

Real environments need a real Vault, their own values and AppRole sign-in — the default; only the `local` profile uses
the root token. Spring Cloud stays on 2025.1, which was built for Boot 4.0, until 2026.0 is released.

## Demo data

The `local` profile loads demo data, so every local run has it: Capitec's personal and business categories and
services, plus the configured branch's hours, capacity pools, offerings and holidays.
`branchmanagement/infrastructure/demo/DemoDataLoader` loads it from `src/main/resources/demo/branch-management.json`
through the command handlers, and is idempotent. Reset with `docker compose down -v`.

The same profile adds 10 made-up staff-created customers from `demo/customers.json`. One of them carries the email
`customer@appointments.test`, so that user's first `POST /api/v1/customers/me` links the two.

## Tests

```bash
mvn verify     # needs Docker running for Testcontainers
```

Tests share one database and the one branch, so:

- Fixtures keep the branch on `ModuleFixtures.EVERY_DAY` hours.
- Each test gets its own capacity pools and services.
- A test that adds a calendar exception uses `ModuleFixtures.uniqueDate()`.
- **Assert on your own rows, not on totals.**

Tests also run a Redis container, turn the rate limits and the outbox job off, replace the notification providers with
`FakeSenders` and drive the dispatcher themselves (`appointments.notification.dispatcher-enabled=false` in
`src/test/resources/application.properties`), and disable the Keycloak admin calls
(`appointments.identity.keycloak-admin.enabled=false`).

Test `@DisplayName`s start with the requirement ID they cover (`BM-n`, `IA-n`, `CU-n`, `BK-n`, `NT-n`) — see
[docs/traceability.md](docs/traceability.md). `ArchitectureTests` writes the generated module documentation to
`target/spring-modulith-docs`.

## API conventions

### Versioning

Every endpoint lives under `/api/v1` (path versioning); unversioned `/api/...` paths are refused. Adding endpoints,
fields or optional parameters stays in v1. A breaking change — a removed or renamed field or path, a new required
parameter, a changed meaning — goes into `/api/v2` alongside v1. Details in
[docs/conventions.md](docs/conventions.md#api-versioning).

### Paging, validation errors and caching

- Unbounded lists return `PageResponse`: `page` starts at 0, `size` is 1 to 100 and defaults to 20.
- A `400` for invalid input lists each offending field under `errors`; module advices extend `ProblemDetailsSupport`.
- The public branch, services and categories are cached in Redis and emptied when the hours or the catalog change
  (`PublicCatalog`, `PublicCatalogEviction`).

Details in [docs/conventions.md](docs/conventions.md#paging-validation-errors-and-caching).

### API documentation

Swagger UI is at `/swagger-ui.html` with the groups `admin` and `public`; its Authorize button signs in through
Keycloak. The OpenAPI JSON is at `/v3/api-docs/{group}`. Every controller has an `@Tag` and every endpoint an
`@Operation` summary — keep them up to date. Neither is exposed in production.

## Security

Tokens are validated in `identity` only. No other module reads a token or trusts role claims. To restrict an
endpoint, use `@RequiresRole` on the controller — checked before the request body is read — or the `AccessControl`
facade, and register public paths with a `PublicEndpoints` bean. Details in
[docs/conventions.md](docs/conventions.md).

### Personal data

Names, emails and phone numbers are stored only in the `customer` module. Events, log lines and other modules carry a
`CustomerId` and ask `CustomerDirectory` when they need details. Staff access to customer data is written to
`customer.access_audit`. Erasing a customer also deletes their Keycloak login after the commit, through Identity's
`LoginAccounts` using the `appointments-backend` service account — unless the person is staff.

### Staff invitations

After an invitation or re-invitation commits, `StaffInvitationListener` has Keycloak create a login for the person,
marked with the attribute `staffMemberId`, and send its "execute actions" email: verify email, set a password, set up
an authenticator. The link is valid until the invitation expires and returns to `STAFF_SETUP_REDIRECT_URL`. Someone
who already has a login only gets the verify link, and only if their email is unverified; otherwise they get nothing
and sign in as usual. Withdrawing an invitation deletes only the login that was made for it. Activation still happens
at first sign-in by verified email.

The port is `StaffAccounts`, implemented by `KeycloakStaffAccounts`; tests replace it. The realm's user profile allows
admin-only unmanaged attributes for the marker. `signUpUrl` is still returned by the invite endpoint in v1, but the
frontend no longer shows it.

## Notifications

Emails go to Mailpit locally — SMTP on port 1025, inbox at http://localhost:8025. SMS is a logging stand-in until a
provider is chosen.

`notification` stores IDs only and reads contact details, appointment state and the branch name at send time. Nothing
depends on it.

## Events and rate limits

Failed or stuck event listeners are retried every 5 minutes for up to 12 attempts, and completed publications are
deleted after 7 days (`EventPublicationMaintenance`, `appointments.events`). **Listeners must be idempotent.**

Rate limits are applied per user, or per client address when there is no token, with a smaller limit for booking
changes. Exceeding one returns `429` with `Retry-After` (`RequestRateLimiter` in `identity`,
`appointments.identity.rate-limits`).

`booking` is the only module that enforces capacity, and the only place where two aggregates change in one
transaction — an appointment and its pool days. Details in [docs/conventions.md](docs/conventions.md#booking).

## Observability

Metrics, traces and logs go over OTLP to the `monitoring` service (grafana/otel-lgtm), with Grafana at
http://localhost:3000 and the dashboard "Appointments overview". Keycloak and PostgreSQL (via postgres-exporter) are
scraped too. Over HTTP only `/actuator/health` and `/actuator/info` are exposed.

Module metrics go through a `<Module>Metrics` port with bounded, anonymous labels. Dashboards and alerts are code in
`monitoring/`. Details in [docs/conventions.md](docs/conventions.md#monitoring).

## Container image

```bash
docker build -t branch-appointments-backend .
```

The `Dockerfile` builds the jar from source without running tests: a Maven stage, then Spring Boot layers on a Java 21
JRE, a non-root user and port 8081.

The image is configured at run time by environment variables only, and runs as production unless
`SPRING_PROFILES_ACTIVE=local` is set. Secrets still come from Vault.

| Variable | Purpose |
| --- | --- |
| `VAULT_URI`, `VAULT_ROLE_ID`, `VAULT_SECRET_ID` | Vault address and AppRole credentials |
| `DATABASE_URL` | PostgreSQL JDBC URL |
| `REDIS_HOST` | Cache host |
| `SMTP_HOST`, `NOTIFICATION_EMAIL_FROM` | Outgoing email |
| `JWT_ISSUER_URI` | Keycloak **as the browser sees it** — every token's `iss` |
| `KEYCLOAK_URL` | Where the backend calls Keycloak for keys, health and the admin API (`appointments.identity.keycloak-url`, defaults to the issuer), e.g. `http://keycloak:8080/realms/appointments` in compose |
| `FRONTEND_URL` | Allowed origin and link base |
| `OTLP_ENDPOINT` | Telemetry collector |
| `BRANCH_*` | The branch profile — code, name, address, location, time zone, segments |

## Continuous integration

`.github/workflows/backend-image.yml` builds and tests every push and pull request. From `main` it pushes
`<DOCKERHUB_USERNAME>/branch-appointments-backend` for amd64 and arm64 to Docker Hub as `latest` and `sha-...`, and
from `v*.*.*` tags as `1.2.3` and `1.2`.

It needs the repository variable `DOCKERHUB_USERNAME` and the secret `DOCKERHUB_TOKEN` (a Docker Hub access token).

## Related repositories

| Repository | Contents |
| --- | --- |
| `../Branch Appointments Frontend Angular` | Angular app on port 4200; proxies `/api` to 8081 |
| `../Branch Appointments Fullstack` | Runs the whole app from images — backend, frontend and every service above |

## Working on the code

Module conventions, database rules and jMolecules gotchas are in [docs/conventions.md](docs/conventions.md). Follow
the one-schema-per-context rules there even where a module spec mentions table prefixes.

## Further documentation

- [docs/conventions.md](docs/conventions.md) — module layout, security, versioning, paging, database, events, booking,
  notification, personal data, monitoring
- [docs/traceability.md](docs/traceability.md) — requirement-to-test mapping
- `target/spring-modulith-docs` — generated module documentation, written by `ArchitectureTests`
