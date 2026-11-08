-- Identity owns the identity schema: the branch's staff and their roles.

CREATE TABLE identity.staff_member
(
    id           UUID         NOT NULL PRIMARY KEY,
    subject      VARCHAR(255),
    email        VARCHAR(254),
    display_name VARCHAR(120) NOT NULL,
    role         VARCHAR(32)  NOT NULL CHECK (role IN ('BRANCH_STAFF', 'BRANCH_MANAGER')),
    status       VARCHAR(16)  NOT NULL CHECK (status IN ('INVITED', 'ACTIVE', 'SUSPENDED', 'REMOVED')),
    invited_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    version      BIGINT       NOT NULL,
    CONSTRAINT staff_member_subject_uk UNIQUE (subject),
    CONSTRAINT staff_member_email_ck CHECK (status = 'REMOVED' OR email IS NOT NULL),
    CONSTRAINT staff_member_removed_ck CHECK (status <> 'REMOVED' OR (email IS NULL AND subject IS NULL))
);

-- An email belongs to at most one staff member who is not removed
CREATE UNIQUE INDEX staff_member_email_uk ON identity.staff_member (email) WHERE status <> 'REMOVED';
CREATE INDEX staff_member_role_status_idx ON identity.staff_member (role, status);

CREATE TABLE identity.staff_change_log
(
    id              UUID          NOT NULL PRIMARY KEY,
    -- Checked at commit: the row is written by JDBC before Hibernate flushes a new staff member
    staff_member_id UUID          NOT NULL REFERENCES identity.staff_member (id) DEFERRABLE INITIALLY DEFERRED,
    action          VARCHAR(32)   NOT NULL CHECK (action IN ('INVITED', 'REINVITED', 'ACTIVATED', 'ROLE_CHANGED',
                                                             'SUSPENDED', 'REINSTATED', 'REMOVED')),
    changed_by      UUID,
    detail          VARCHAR(1000) NOT NULL,
    occurred_at     TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX staff_change_log_member_idx ON identity.staff_change_log (staff_member_id, occurred_at);

-- The change log is append-only
CREATE FUNCTION identity.reject_change_log_modification() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'identity.staff_change_log is append-only';
END;
$$;

CREATE TRIGGER staff_change_log_append_only
    BEFORE UPDATE OR DELETE ON identity.staff_change_log
    FOR EACH ROW EXECUTE FUNCTION identity.reject_change_log_modification();
