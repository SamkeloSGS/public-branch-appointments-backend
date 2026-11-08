# Conventions for every module

These rules apply to every bounded context in this modular monolith (Branch management, Booking, Customer,
Notification, Identity). The application runs for one branch: its name, address, time zone and customer segments
are configuration (`appointments.branch`, the `BranchProfile`), and no table, event or API carries a branch ID. The Branch management spec section 3 holds the general Spring Modulith and jMolecules rules;
this page records decisions made on top of it.

## Package layout inside a module

```
<module>/                     public API: identifiers, events, shared value types, query facade
  domain/
    entity/                   aggregate roots
    vo/                       value objects
    repository/               one repository per aggregate
    exception/                domain rule violations
  application/
    command/                  one record per command
    handler/                  one *CommandHandler per aggregate, a handle(command) method per command
    query/                    query services, the query facade implementation, read models
    exception/                not found, stale version
    port/                     interfaces implemented by infrastructure
    support/                  helpers shared by handlers and queries
  infrastructure/
    config/                   clock and module configuration
    persistence/              Flyway and database adapters
    security/                 public endpoints (identity: the resource server and access checks)
  web/
    controller/               REST controllers and the ETag helper
    dto/                      DTOs shared by requests and responses
      request/  response/
    error/                    Problem Details mapping
```

The layer packages (`domain`, `application`, `infrastructure`, `web`) contain only their `package-info.java`;
every class lives in a sub-package, and every sub-package has a `package-info.java` with its onion ring
annotation. Tests mirror the package of the code they test.

The module root package is the published API and holds only what other modules consume: the domain events, the
query facade (`BranchDirectory`), and the identifiers, value types and enums their signatures expose. Helpers needed
only inside the module go in a sub-package or stay package-private (like `Validation`, which only `Address` uses).
`PublishedApiTests` enforces this: every public root type must be reachable from an event or the facade, and the
contract must not expose module-internal or framework types.

## Security and access

Owned by the `identity` module; other modules depend on its public API only.

- **Tokens** are validated once, in `identity`. The checks are signature against Keycloak's keys, issuer, the
  `appointments-api` audience, and expiry with 30 s tolerance. No other module reads a token, and no code trusts
  role claims: roles come from `identity.staff_member`.
- **Admin paths** (`/api/v1/admin/**`) need an ACTIVE staff member, and a token at level 2 (`acr` = 2, password plus
  one-time code). A level-1 token gets `401` with `WWW-Authenticate: Bearer error="insufficient_user_authentication"`
  so the frontend can step up. Anyone else gets `403`.
- **Role checks:** put `@RequiresRole(StaffRole.X)` on controllers or methods. An interceptor in `identity` enforces
  it before the request body is read, so callers without access get `403`, never `400`. Inside application code, use
  `AccessControl` (`requireRole`, `currentUser`). Role order: the branch manager includes branch staff. Managing the
  branch (hours, catalog, pools, offerings, calendar, staff, customer status and erasure, schedule impact) needs the
  branch manager; customers, appointments and notifications are open to all branch staff.
- **Public paths:** each module registers a `PublicEndpoints` bean listing its token-free endpoints (e.g.
  `BranchManagementPublicEndpoints`); the filter chain collects them. Everything else under `/api/v1` needs a token.
- **Rate limits** (`RequestRateLimiter`, `appointments.identity.rate-limits`): token buckets per signed-in user, or per
  client address without a token, plus a smaller bucket for booking changes (non-GET `/api/v1/appointments/**`). A
  refusal is `429` with `Retry-After` and the code `RATE_LIMITED`. Actuator paths are exempt. Buckets are in memory,
  so with several instances the limit is per instance; behind a proxy set `server.forward-headers-strategy`.
- **Keycloak** is configured as code in `keycloak/appointments-realm.json`. Environment-specific values are
  `${KC_...}` placeholders, set in `compose.yaml` locally. Local test users live separately in `keycloak/local/`.
  `AdminAccessMatrixTests` tries every admin endpoint of every module with a level-1 token and a customer, and every
  manager-only endpoint with branch staff.

## API versioning

- **Path versioning:** every endpoint is under `/api/v1` (public `/api/v1/...`, staff `/api/v1/admin/...`, provider
  callbacks `/api/v1/webhooks/...`). The filter chain only serves `/api/v1/**`, so an unversioned `/api/...` path is
  refused. Swagger groups and `PublicEndpoints` patterns use the versioned paths.
- **What stays in v1:** new endpoints, new response fields, new optional request fields or parameters, and new enum
  values that clients can ignore. Clients must ignore response fields they do not know.
- **What needs v2:** removing or renaming a field or path, making a parameter required, changing a field's type or
  meaning, or changing status codes or error `code`s that clients act on. Add `/api/v2` endpoints next to v1 (for
  example a second controller method or class), never change v1 in place.
- **Retiring a version:** announce it in the changelog and send `Deprecation` and `Sunset` response headers on the old
  version for at least six months before removing it.

## Paging, validation errors and caching

The shared module (`com.samdev.appointments.shared`) holds these web conventions. It is not a bounded context: it has
no data and no events, and depends on no module.

- **Paged lists:** a list that grows without bound returns a `PageResponse`, which is
  `{ "items": [...], "page": 0, "size": 20, "totalItems": 57, "totalPages": 3 }`. Callers choose the page with
  `page` (from 0) and `size` (1 to 100, default 20), and the controller declares both with `@Min`/`@Max`. The paged
  lists are customer search (`/api/v1/admin/customers`), the customer's own appointments
  (`/api/v1/appointments/mine`) and recent notifications (`/api/v1/admin/notifications`). Lists bounded by the
  branch itself are returned whole: the catalog, pools, offerings, the staff, one day's appointments, one
  appointment's notifications and upcoming calendar entries. Paging went into v1 directly because v1 had not been
  released yet; any later change from a plain array to a page needs `/api/v2`.
- **Validation errors:** every module's Problem Details advice extends `ProblemDetailsSupport`. A request that fails
  bean validation, has a parameter of the wrong type, or has a body value that cannot be read is a `400` with
  `title` "Invalid request" and an `errors` list such as `[{ "field": "givenName", "message": "must not be blank" }]`.
  The field is the body property path (`hours[0].start`) or the query parameter or header name. A rule across
  fields has no `field`. Messages are English and never repeat the rejected value. Domain rules checked in value
  objects (an invalid email, for example) stay a `400` with the reason in `detail`.
- **Caching:** public reads are cached in Redis (`spring.cache.redis`, `CacheConfiguration`), so every instance
  shares one cache. Branch management's `PublicCatalog` caches `GET /api/v1/branch`, `/services` and
  `/service-categories` as the responses themselves, stored as plain JSON with no type information.
  `PublicCatalogEviction` empties those caches after any change to the hours or the catalog commits, and at start-up
  (the branch profile comes from configuration). The 10-minute time to live is a safety net, not the way changes
  reach customers. Cache failures are logged and the request reads the database instead, so Redis being down slows
  the site but never breaks it. Availability is never cached: it changes with every booking. Redis is only a cache
  and is safe to flush (`docker compose restart redis`). A new cache gets its own typed serializer and its own
  eviction on the events that change it.

## Demo data

`demo/branch-management.json` holds Capitec's website menus (categories and services), captured 2026-10-10, and
how to set up the configured branch: opening hours, pool sizes and Smart ID services. The following are assumptions
for testing:

- Service durations, opening hours and pool sizes.
- Holidays and overrides, generated relative to the load date.

The branch's name, address and segments come from `appointments.branch`; the loader offers only services of the
segments it serves.

The loader only runs with the `local` profile and uses the command handlers, so it follows every domain rule and
publishes the normal events. Keep the JSON valid against the commands when the model changes;
`DemoDataLoaderTests` loads it on every build.

## Database

- **One PostgreSQL schema per bounded context.** Table names carry no module prefix: Branch management uses
  `branch_management.service`, Customer uses `customer.customer`, Booking uses `booking.appointment`. Entities declare
  `@Table(schema = "<schema>", name = "<table>")`.
- **No foreign keys across schemas.** A column such as `service_id` in Booking is a plain value.
  `DatabaseCatalogTests` reads `pg_constraint` and fails the build if a cross-schema foreign key appears.
- **No joins across schemas.** Modules see only their own repositories (enforced by `ApplicationModules.verify()`).
  Hand-written SQL is covered by code review.
- **Each module has its own migration folder and Flyway history**, so its tables can later move to another database:
  `src/main/resources/db/migration/<schema>/`, migrated by a `FlywayMigrationInitializer` bean in the module's
  `infrastructure` package with `schemas("<schema>")` and its history table in that schema
  (see `BranchManagementFlywayConfiguration`). Spring Boot's own Flyway auto-configuration is disabled.
- **The event publication registry is the only shared table**: `public.event_publication`, migrated from
  `db/migration/shared` by `SharedPersistenceConfiguration` (history `public.flyway_schema_history_shared`).
- Hibernate runs with `ddl-auto: validate`, so a mapping that drifts from the migrations fails at startup.

## jMolecules ByteBuddy gotchas

- The plugin derives `@Entity`, `@EmbeddedId`, `@Embeddable`, association converters and `@ElementCollection`, but
  not table/schema names, spec column names, `@Version` or `@Enumerated(STRING)`; add those as annotations.
- Any method whose name starts with `with` is treated as a wither and must return an instance; a `void withdraw()`
  fails the build. Return `this` (see `ServiceOffering.withdraw()`).
- A class implementing `ValueObject` becomes `@Embeddable` and is scanned by Hibernate. Keep non-persisted value
  types (such as `WeeklyHours`) off the interface.

## Events

- Listeners in other modules use `@ApplicationModuleListener`; the application enables `@Async`, so they run after
  commit on another thread and a failure leaves the publication incomplete in the registry.
- **Listeners must be idempotent**: a failed or stuck publication is delivered again. Spring Modulith marks a
  listener that threw as FAILED, and one that has not finished after 10 minutes too (`spring.modulith.events.staleness`);
  `EventPublicationMaintenance` resubmits FAILED publications every 5 minutes, up to 12 attempts in all, then leaves
  them FAILED for a person (the outbox gauges and alert show them; a restart redelivers once more). Completed
  publications are deleted after 7 days. Settings under `appointments.events`.
- Event payloads use identifiers and the module's public value types (`TimeRange`, `Address`, `GeoLocation`, enums),
  never aggregates or `domain` types.
- Listeners that open their own transactions (one per pool day, one per appointment) declare
  `@ApplicationModuleListener(propagation = Propagation.NOT_SUPPORTED)`, so no outer transaction holds a connection
  while they work.
- **No personal data in events.** The publication registry stores serialised events, so a name or email in a
  payload would survive erasure. Identity, Customer and Booking events carry IDs, the booking reference, flags and
  times only; their `*PublishedApiTests` enforce it. Consumers that need contact details call `CustomerDirectory` at the time they
  need them and keep no copy.

## Booking

- **Two aggregates in one transaction**, only here: booking, rescheduling, cancelling and displacing change one
  `Appointment` and one or two `PoolDaySchedule`s together (`AppointmentCommandHandler`). The pool day's version
  decides races; the handler retries once on a version conflict or unique-constraint race, then reports
  `SLOT_UNAVAILABLE`. A PostgreSQL exclusion constraint (`btree_gist`, installed in the `booking` schema) backs the
  "no overlapping appointments per customer" rule across pools.
- **Schedule projection:** `booking.view_*` tables hold Booking's copy of the branch schedule, kept by
  `BranchEventsListener`. Updates are keyed by upstream ID and ignore events no newer than the row's last event or
  the last rebuild (`view_schedule.rebuilt_at`); removals are tombstones; writes are serialised by an advisory lock.
  The projection is built from `BranchDirectory.scheduleSnapshot` at start-up when it is empty
  (`ScheduleProjectionBootstrap`), and rebuilt whenever an event refers to a row it does not know.
- After a projection change, the affected pool days from today on are recomputed, each in its own transaction.
  Zero capacity displaces reservations (an internal `ReservationDisplaced` event turns each appointment into
  `REBOOKING_REQUIRED`); reduced but non-zero capacity only flags the day as overbooked for the branch manager.

## Notification

- Listeners only create or withdraw notifications; the dispatcher sends. Each event is handled in one transaction
  and its source event key (`<event>:<appointment>:<occurredAt>:<type>`) makes a redelivery create nothing. A
  rescheduled, cancelled or needs-rebooking message cancels the appointment's pending messages, and an event older
  than such a message is ignored as stale.
- The dispatcher claims due rows with `FOR UPDATE SKIP LOCKED`, one transaction per notification, and checks the
  appointment's current state before sending. Contact details and language come from `CustomerDirectory.contactFor`
  at that moment. Delivery is at least once; the delivery ID is the provider idempotency key.
- Providers sit behind `EmailSender` and `SmsSender`; they report outcomes (accepted, transient, permanent), never
  throw for ordinary failures, and log delivery IDs only. `ErrorCode.reduce` strips addresses and numbers from
  anything stored in `last_error`.
- Templates are files in `src/main/resources/notification/templates` (`<type>.<channel>.<language>.txt`); the
  application does not start if a type and channel lack an English one. SMS texts must stay within 160 characters
  (`TemplateRendererTests`). Templates exist in English (`en`), Afrikaans (`af`) and isiZulu (`zu`); customers who
  chose isiXhosa (`xh`) or Sesotho (`st`) get English until those are added. The Zulu SMS texts use "Ref" (as Zulu
  bank SMSes do) to stay within one message; the emails say "Inkomba".

## Personal data

- The `customer` module is the only place that stores names, emails and phone numbers. Notification renders
  messages from them at send time and stores neither the details nor the rendered text. Other modules store
  `CustomerId` and ask `CustomerDirectory` (`isActive`, `displayNames`, `contactFor`, `findByIdentity`).
- Exception messages, Problem Details and log lines never repeat a name, email or phone. Self-service conflicts
  about someone else's email say only that the details could not be used (`CONTACT_DETAILS_UNAVAILABLE`).
- Erasure clears the personal columns and keeps the row and ID; a check constraint guarantees nothing is left.
  After the commit, an internal `LoginAccountErasureRequested` event makes Customer ask Identity's `LoginAccounts` to
  delete the Keycloak login (skipped for staff, retried by the event registry if Keycloak is down). Only Identity
  talks to Keycloak.
- Staff search, view and changes are written to `customer.access_audit` in the same transaction (fail closed);
  self-service and facade calls are not. Rows are append-only (trigger) and purged after 24 months by
  `AccessAuditRetention`.

## Monitoring

- The backend pushes metrics, traces and logs over OTLP to an OpenTelemetry collector (`management.*` in
  `application.yml`, `OTLP_ENDPOINT`). Locally that is the `monitoring` service in `compose.yaml` (grafana/otel-lgtm:
  Grafana on http://localhost:3000, Prometheus, Tempo, Loki). Nothing is exported in tests.
- Over HTTP only `/actuator/health` (with `/liveness` and `/readiness`) and `/actuator/info` are exposed, public and
  without details. Health includes `keycloak` (`KeycloakHealthIndicator`), but readiness does not depend on it.
- Spring Modulith observability adds a span for each call into a module's API and each event listener, so a trace
  shows the modules a request crossed. Each SQL statement is a span too (`datasource-micrometer`, `jdbc.*` in
  `application.yml`), with literals replaced by `?` and never the parameter values. Log lines carry the trace and
  span IDs.
- **Module metrics go through a port**: handlers call `application/port/<Module>Metrics`, implemented with
  Micrometer in `infrastructure/metrics`, which also registers the module's gauges (SQL counts over its own schema,
  read on each export). Gauges of the shared event registry are in `MonitoringConfiguration`.
- **Labels are bounded and anonymous**: operations, actors, channels, outcomes and rejection or reason codes. Never an
  ID, a reference, a customer, an email or a URL with an ID in it (Spring's `uri` label is the route template). The
  same applies to span attributes.
- Dashboards (`monitoring/dashboards/*.json`) and alert rules (`monitoring/provisioning/alerting.yaml`) are code,
  provisioned when Grafana starts; edits made in the Grafana UI are not kept. The overview dashboard is generated:
  keep it readable when editing by hand. Alert emails go to Mailpit locally.
- Keycloak exposes Prometheus metrics on its management port (`KC_METRICS_ENABLED`, user event metrics for sign-in
  outcomes) and PostgreSQL through `postgres-exporter`; the collector scrapes both
  (`monitoring/otelcol-appointments.yaml`).
