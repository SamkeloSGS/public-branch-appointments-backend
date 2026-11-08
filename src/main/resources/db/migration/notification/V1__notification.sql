-- Notification owns the notification schema. It stores IDs, types, statuses and times only: no name, address,
-- phone number or message text. Customer and appointment IDs are plain values.

CREATE TABLE notification.notification
(
    id               UUID         NOT NULL PRIMARY KEY,
    source_event_key VARCHAR(200) NOT NULL,
    customer_id      UUID         NOT NULL,
    appointment_id   UUID         NOT NULL,
    type             VARCHAR(20)  NOT NULL CHECK (type IN ('CONFIRMATION', 'RESCHEDULED', 'CANCELLATION',
                                                           'REBOOKING_REQUIRED', 'REMINDER')),
    status           VARCHAR(16)  NOT NULL CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'SKIPPED', 'CANCELLED')),
    status_reason    VARCHAR(24) CHECK (status_reason IN ('CUSTOMER_UNAVAILABLE', 'NO_CHANNEL', 'APPOINTMENT_CHANGED',
                                                          'TOO_LATE')),
    send_after       TIMESTAMP WITH TIME ZONE NOT NULL,
    -- When the triggering event happened, so a late older event can be recognised as stale
    event_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    version          BIGINT       NOT NULL,
    -- Handling an event twice creates nothing new
    CONSTRAINT notification_source_event_key_uk UNIQUE (source_event_key),
    CONSTRAINT notification_reason_ck CHECK ((status = 'SKIPPED') = (status_reason IS NOT NULL))
);

-- The dispatcher's query
CREATE INDEX notification_due_idx ON notification.notification (send_after) WHERE status = 'PENDING';
CREATE INDEX notification_appointment_idx ON notification.notification (appointment_id);
CREATE INDEX notification_customer_idx ON notification.notification (customer_id, status);
CREATE INDEX notification_created_idx ON notification.notification (created_at, status);

-- Belongs to the notification aggregate and is loaded and saved only through it
CREATE TABLE notification.delivery
(
    id                  UUID        NOT NULL PRIMARY KEY,
    notification_id     UUID        NOT NULL REFERENCES notification.notification (id) ON DELETE CASCADE,
    channel             VARCHAR(8)  NOT NULL CHECK (channel IN ('EMAIL', 'SMS')),
    status              VARCHAR(16) NOT NULL CHECK (status IN ('RETRYING', 'SENT', 'FAILED', 'DELIVERED', 'BOUNCED')),
    attempts            INTEGER     NOT NULL CHECK (attempts >= 0),
    provider_message_id VARCHAR(255),
    -- A provider error code or category only
    last_error          VARCHAR(64),
    last_attempt_at     TIMESTAMP WITH TIME ZONE,
    sent_at             TIMESTAMP WITH TIME ZONE,
    CONSTRAINT delivery_channel_uk UNIQUE (notification_id, channel)
);

CREATE INDEX delivery_provider_message_idx ON notification.delivery (provider_message_id);
