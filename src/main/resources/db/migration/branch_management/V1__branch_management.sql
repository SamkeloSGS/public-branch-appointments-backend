-- Branch management owns the branch_management schema. Foreign keys stay inside this schema;
-- no other module reads, joins or references these tables.
-- The app runs for one branch, whose name, address, time zone and customer segments come from configuration
-- (appointments.branch); nothing here refers to a branch.

-- The branch's weekly hours: a single row (OpeningHours.ID), created here so it can be edited with If-Match
CREATE TABLE branch_management.opening_hours
(
    id      UUID   NOT NULL PRIMARY KEY,
    version BIGINT NOT NULL
);

INSERT INTO branch_management.opening_hours (id, version)
VALUES ('00000000-0000-0000-0000-000000000001', 0);

CREATE TABLE branch_management.opening_hours_period
(
    opening_hours_id UUID       NOT NULL REFERENCES branch_management.opening_hours (id) ON DELETE CASCADE,
    day_of_week      VARCHAR(9) NOT NULL,
    opens_at         TIME       NOT NULL,
    closes_at        TIME       NOT NULL,
    CONSTRAINT opening_hours_period_range_ck CHECK (closes_at > opens_at)
);

CREATE INDEX opening_hours_period_hours_idx ON branch_management.opening_hours_period (opening_hours_id);

-- Service categories, each for one customer segment (PERSONAL or BUSINESS)
CREATE TABLE branch_management.service_category
(
    id          UUID         NOT NULL PRIMARY KEY,
    name        VARCHAR(80)  NOT NULL,
    segment     VARCHAR(16)  NOT NULL CHECK (segment IN ('PERSONAL', 'BUSINESS')),
    description VARCHAR(500),
    active      BOOLEAN      NOT NULL,
    version     BIGINT       NOT NULL,
    -- Target of the composite foreign key below, so a service's segment always matches its category's
    CONSTRAINT service_category_id_segment_uk UNIQUE (id, segment)
);

CREATE UNIQUE INDEX service_category_segment_name_uk ON branch_management.service_category (segment, lower(name));

CREATE TABLE branch_management.service
(
    id                   UUID        NOT NULL PRIMARY KEY,
    name                 VARCHAR(80) NOT NULL,
    description          VARCHAR(500),
    default_duration_min INT         NOT NULL CHECK (default_duration_min > 0),
    category_id          UUID        NOT NULL,
    segment              VARCHAR(16) NOT NULL CHECK (segment IN ('PERSONAL', 'BUSINESS')),
    active               BOOLEAN     NOT NULL,
    version              BIGINT      NOT NULL,
    CONSTRAINT service_category_fk FOREIGN KEY (category_id, segment)
        REFERENCES branch_management.service_category (id, segment)
);

CREATE UNIQUE INDEX service_name_uk ON branch_management.service (lower(name));
CREATE INDEX service_category_id_idx ON branch_management.service (category_id);

CREATE TABLE branch_management.capacity_pool
(
    id            UUID        NOT NULL PRIMARY KEY,
    name          VARCHAR(80) NOT NULL,
    base_capacity INT         NOT NULL CHECK (base_capacity >= 1),
    version       BIGINT      NOT NULL
);

CREATE UNIQUE INDEX capacity_pool_name_uk ON branch_management.capacity_pool (lower(name));

CREATE TABLE branch_management.service_offering
(
    id                UUID    NOT NULL PRIMARY KEY,
    service_id        UUID    NOT NULL REFERENCES branch_management.service (id),
    capacity_pool_id  UUID    NOT NULL REFERENCES branch_management.capacity_pool (id),
    duration_min      INT     NOT NULL CHECK (duration_min > 0),
    slot_interval_min INT     NOT NULL CHECK (slot_interval_min BETWEEN 5 AND 120),
    min_lead_time_min INT     NOT NULL CHECK (min_lead_time_min >= 0),
    max_advance_days  INT     NOT NULL CHECK (max_advance_days BETWEEN 1 AND 365),
    active            BOOLEAN NOT NULL,
    version           BIGINT  NOT NULL,
    CONSTRAINT service_offering_service_uk UNIQUE (service_id),
    CONSTRAINT service_offering_duration_multiple_ck CHECK (duration_min % slot_interval_min = 0)
);

CREATE TABLE branch_management.calendar_exception
(
    id             UUID        NOT NULL PRIMARY KEY,
    exception_date DATE        NOT NULL,
    type           VARCHAR(16) NOT NULL CHECK (type IN ('CLOSED', 'SPECIAL_HOURS')),
    reason         VARCHAR(200),
    version        BIGINT      NOT NULL,
    CONSTRAINT calendar_exception_date_uk UNIQUE (exception_date)
);

CREATE TABLE branch_management.calendar_exception_hours
(
    exception_id UUID NOT NULL REFERENCES branch_management.calendar_exception (id) ON DELETE CASCADE,
    opens_at     TIME NOT NULL,
    closes_at    TIME NOT NULL,
    CONSTRAINT calendar_exception_hours_range_ck CHECK (closes_at > opens_at)
);

CREATE INDEX calendar_exception_hours_exception_idx ON branch_management.calendar_exception_hours (exception_id);

CREATE TABLE branch_management.capacity_override
(
    id               UUID        NOT NULL PRIMARY KEY,
    capacity_pool_id UUID        NOT NULL REFERENCES branch_management.capacity_pool (id),
    override_date    DATE        NOT NULL,
    from_time        TIME,
    to_time          TIME,
    capacity         INT         NOT NULL CHECK (capacity >= 0),
    reason           VARCHAR(16) NOT NULL
        CHECK (reason IN ('SHORT_STAFFED', 'LEAVE', 'TRAINING', 'OUTAGE', 'EXTRA_STAFF', 'OTHER')),
    note             VARCHAR(200),
    version          BIGINT      NOT NULL,
    CONSTRAINT capacity_override_window_ck CHECK (
        (from_time IS NULL AND to_time IS NULL) OR (from_time IS NOT NULL AND to_time IS NOT NULL AND to_time > from_time))
);

CREATE INDEX capacity_override_pool_date_idx ON branch_management.capacity_override (capacity_pool_id, override_date);
CREATE INDEX capacity_override_date_idx ON branch_management.capacity_override (override_date);

-- Backstop for concurrent requests: at most one whole-day override per pool and date.
-- Overlapping windowed overrides are rejected by the application service, which serializes concurrent
-- requests for one pool with a transaction-scoped advisory lock (PostgresCapacityPoolLocks).
CREATE UNIQUE INDEX capacity_override_whole_day_uk
    ON branch_management.capacity_override (capacity_pool_id, override_date)
    WHERE from_time IS NULL;
