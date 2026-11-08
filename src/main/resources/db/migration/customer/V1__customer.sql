-- Customer owns the customer schema: the only place in the system that stores personal data.

CREATE TABLE customer.customer
(
    id                 UUID        NOT NULL PRIMARY KEY,
    identity_subject   VARCHAR(255),
    given_name         VARCHAR(80),
    family_name        VARCHAR(80),
    email              VARCHAR(254),
    phone              VARCHAR(16),
    email_enabled      BOOLEAN     NOT NULL,
    sms_enabled        BOOLEAN     NOT NULL,
    preferred_language VARCHAR(3)  NOT NULL,
    status             VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE', 'DEACTIVATED', 'ERASED')),
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    version            BIGINT      NOT NULL,
    CONSTRAINT customer_identity_subject_uk UNIQUE (identity_subject),
    -- Unless erased: a name, at least one contact detail, channels only with their detail, at least one channel
    CONSTRAINT customer_live_ck CHECK (status = 'ERASED' OR (
        given_name IS NOT NULL AND family_name IS NOT NULL
        AND (email IS NOT NULL OR phone IS NOT NULL)
        AND (NOT email_enabled OR email IS NOT NULL)
        AND (NOT sms_enabled OR phone IS NOT NULL)
        AND (email_enabled OR sms_enabled))),
    -- Erased: no personal data left
    CONSTRAINT customer_erased_ck CHECK (status <> 'ERASED' OR (
        identity_subject IS NULL AND given_name IS NULL AND family_name IS NULL AND email IS NULL AND phone IS NULL
        AND NOT email_enabled AND NOT sms_enabled))
);

-- An email belongs to at most one customer who is not erased
CREATE UNIQUE INDEX customer_email_uk ON customer.customer (email) WHERE status <> 'ERASED';
CREATE INDEX customer_phone_idx ON customer.customer (phone);
-- Prefix search on names (text_pattern_ops lets LIKE 'abc%' use the index)
CREATE INDEX customer_family_given_name_idx
    ON customer.customer (lower(family_name) text_pattern_ops, lower(given_name) text_pattern_ops);
CREATE INDEX customer_given_name_idx ON customer.customer (lower(given_name) text_pattern_ops);

CREATE TABLE customer.access_audit
(
    id            UUID         NOT NULL PRIMARY KEY,
    -- Null for searches. Checked at commit: the row is written by JDBC before Hibernate flushes a new customer
    customer_id   UUID REFERENCES customer.customer (id) DEFERRABLE INITIALLY DEFERRED,
    staff_subject VARCHAR(255) NOT NULL,
    action        VARCHAR(16)  NOT NULL CHECK (action IN ('SEARCH', 'VIEW', 'CREATE', 'UPDATE', 'DEACTIVATE',
                                                          'REACTIVATE', 'ERASE')),
    search_query  VARCHAR(200),
    occurred_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT access_audit_target_ck CHECK ((action = 'SEARCH') = (customer_id IS NULL))
);

CREATE INDEX access_audit_customer_idx ON customer.access_audit (customer_id, occurred_at);
CREATE INDEX access_audit_staff_idx ON customer.access_audit (staff_subject, occurred_at);

-- The access audit is append-only. Rows older than 24 months may be deleted by the retention job
-- (AccessAuditRetention.RETENTION_MONTHS); nothing else may change or remove a row.
CREATE FUNCTION customer.protect_access_audit() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    IF TG_OP = 'DELETE' AND OLD.occurred_at < now() - INTERVAL '24 months' THEN
        RETURN OLD;
    END IF;
    RAISE EXCEPTION 'customer.access_audit is append-only';
END;
$$;

CREATE TRIGGER access_audit_append_only
    BEFORE UPDATE OR DELETE ON customer.access_audit
    FOR EACH ROW EXECUTE FUNCTION customer.protect_access_audit();
