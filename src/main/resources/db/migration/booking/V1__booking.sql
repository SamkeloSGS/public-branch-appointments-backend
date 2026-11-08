-- Booking owns the booking schema. Customer, service and pool IDs are plain values: no foreign keys to other
-- modules. No personal data is stored here.

-- For the exclusion constraint on (customer, time range); trusted extension, installed into this schema
CREATE EXTENSION IF NOT EXISTS btree_gist SCHEMA booking;

CREATE TABLE booking.appointment
(
    id               UUID        NOT NULL PRIMARY KEY,
    reference        VARCHAR(8)  NOT NULL,
    customer_id      UUID        NOT NULL,
    service_id       UUID        NOT NULL,
    capacity_pool_id UUID        NOT NULL,
    local_date       DATE        NOT NULL,
    start_time       TIME        NOT NULL,
    end_time         TIME        NOT NULL,
    starts_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    ends_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    status           VARCHAR(20) NOT NULL CHECK (status IN ('BOOKED', 'CHECKED_IN', 'COMPLETED', 'NO_SHOW',
                                                            'REBOOKING_REQUIRED', 'CANCELLED')),
    booked_by        VARCHAR(8)  NOT NULL CHECK (booked_by IN ('CUSTOMER', 'STAFF')),
    closed_reason    VARCHAR(20) CHECK (closed_reason IN ('CUSTOMER_CANCELLED', 'STAFF_CANCELLED', 'REBOOKING_EXPIRED',
                                                          'CUSTOMER_ERASED')),
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    version          BIGINT      NOT NULL,
    CONSTRAINT appointment_reference_uk UNIQUE (reference),
    CONSTRAINT appointment_time_ck CHECK (ends_at > starts_at AND end_time > start_time),
    CONSTRAINT appointment_closed_reason_ck CHECK ((status = 'CANCELLED') = (closed_reason IS NOT NULL)),
    -- Backs the overlap rule of the booking policy: two simultaneous bookings in different pools cannot both win
    CONSTRAINT appointment_customer_no_overlap EXCLUDE USING gist (
        customer_id WITH =, tstzrange(starts_at, ends_at) WITH &&) WHERE (status IN ('BOOKED', 'CHECKED_IN'))
);

CREATE INDEX appointment_customer_idx ON booking.appointment (customer_id, starts_at);
CREATE INDEX appointment_date_idx ON booking.appointment (local_date);
CREATE INDEX appointment_status_idx ON booking.appointment (status, ends_at);

CREATE TABLE booking.pool_day_schedule
(
    id               UUID    NOT NULL PRIMARY KEY,
    capacity_pool_id UUID    NOT NULL,
    schedule_date    DATE    NOT NULL,
    overbooked       BOOLEAN NOT NULL,
    version          BIGINT  NOT NULL,
    CONSTRAINT pool_day_schedule_pool_date_uk UNIQUE (capacity_pool_id, schedule_date)
);

CREATE INDEX pool_day_schedule_date_idx ON booking.pool_day_schedule (schedule_date);
CREATE INDEX pool_day_schedule_overbooked_idx ON booking.pool_day_schedule (schedule_date) WHERE overbooked;

-- Both belong to the schedule aggregate and are loaded and saved only through it
CREATE TABLE booking.capacity_segment
(
    schedule_id UUID    NOT NULL REFERENCES booking.pool_day_schedule (id) ON DELETE CASCADE,
    from_time   TIME    NOT NULL,
    to_time     TIME    NOT NULL,
    capacity    INTEGER NOT NULL,
    CONSTRAINT capacity_segment_range_ck CHECK (to_time > from_time),
    CONSTRAINT capacity_segment_capacity_ck CHECK (capacity >= 0)
);

CREATE INDEX capacity_segment_schedule_idx ON booking.capacity_segment (schedule_id);

CREATE TABLE booking.reservation
(
    schedule_id    UUID NOT NULL REFERENCES booking.pool_day_schedule (id) ON DELETE CASCADE,
    appointment_id UUID NOT NULL,
    from_time      TIME NOT NULL,
    to_time        TIME NOT NULL,
    CONSTRAINT reservation_range_ck CHECK (to_time > from_time),
    -- Checked at commit: moving a reservation between two pool days deletes and inserts in either order
    CONSTRAINT reservation_appointment_uk UNIQUE (appointment_id) DEFERRABLE INITIALLY DEFERRED
);

CREATE INDEX reservation_schedule_idx ON booking.reservation (schedule_id);

CREATE TABLE booking.idempotency_key
(
    customer_id     UUID         NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    appointment_id  UUID         NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT idempotency_key_pkey PRIMARY KEY (customer_id, idempotency_key)
);

CREATE INDEX idempotency_key_created_idx ON booking.idempotency_key (created_at);

-- Schedule projection: a read-only copy of what Booking needs from Branch management, kept by event listeners.
-- Rows remember the time of the last event applied to them; older events are ignored. Removals are tombstones, so a
-- late "added" event cannot bring a removed row back. view_schedule has one row once the projection is built;
-- rebuilt_at marks the last rebuild from the branch's schedule snapshot, and events no newer than it are already
-- contained in it.

CREATE TABLE booking.view_schedule
(
    id             SMALLINT NOT NULL PRIMARY KEY CHECK (id = 1),
    hours_event_at TIMESTAMP WITH TIME ZONE,
    rebuilt_at     TIMESTAMP WITH TIME ZONE
);

CREATE TABLE booking.view_weekly_hours
(
    day_of_week SMALLINT NOT NULL CHECK (day_of_week BETWEEN 1 AND 7),
    opens_at    TIME     NOT NULL,
    closes_at   TIME     NOT NULL CHECK (closes_at > opens_at),
    PRIMARY KEY (day_of_week, opens_at)
);

CREATE TABLE booking.view_pool
(
    id            UUID    NOT NULL PRIMARY KEY,
    base_capacity INTEGER NOT NULL,
    last_event_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE booking.view_offering
(
    id                UUID    NOT NULL PRIMARY KEY,
    service_id        UUID    NOT NULL,
    capacity_pool_id  UUID    NOT NULL,
    duration_min      INTEGER NOT NULL,
    slot_interval_min INTEGER NOT NULL,
    min_lead_time_min INTEGER NOT NULL,
    max_advance_days  INTEGER NOT NULL,
    active            BOOLEAN NOT NULL,
    last_event_at     TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX view_offering_service_idx ON booking.view_offering (service_id);

CREATE TABLE booking.view_exception
(
    id             UUID        NOT NULL PRIMARY KEY,
    exception_date DATE        NOT NULL,
    type           VARCHAR(16) NOT NULL CHECK (type IN ('CLOSED', 'SPECIAL_HOURS')),
    removed        BOOLEAN     NOT NULL,
    last_event_at  TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX view_exception_date_idx ON booking.view_exception (exception_date);

CREATE TABLE booking.view_exception_hours
(
    exception_id UUID NOT NULL REFERENCES booking.view_exception (id) ON DELETE CASCADE,
    opens_at     TIME NOT NULL,
    closes_at    TIME NOT NULL CHECK (closes_at > opens_at),
    PRIMARY KEY (exception_id, opens_at)
);

CREATE TABLE booking.view_override
(
    id               UUID    NOT NULL PRIMARY KEY,
    capacity_pool_id UUID    NOT NULL,
    override_date    DATE    NOT NULL,
    -- Both empty for a whole-day override
    from_time        TIME,
    to_time          TIME,
    capacity         INTEGER NOT NULL CHECK (capacity >= 0),
    removed          BOOLEAN NOT NULL,
    last_event_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT view_override_window_ck CHECK ((from_time IS NULL) = (to_time IS NULL))
);

CREATE INDEX view_override_pool_date_idx ON booking.view_override (capacity_pool_id, override_date);
