CREATE SCHEMA IF NOT EXISTS moveiq;

CREATE TABLE IF NOT EXISTS moveiq.trip (
    business_unit varchar(64) NOT NULL,
    trip_id bigint NOT NULL,
    trip_date date NOT NULL,
    office varchar(160),
    shift_type varchar(32),
    trip_direction varchar(16),
    vendor_id varchar(160),
    planned_start_epoch bigint,
    planned_end_epoch bigint,
    actual_start_epoch bigint,
    actual_end_epoch bigint,
    reported_delay_minutes integer,
    planned_employee_cnt integer,
    actual_employee_cnt integer,
    noshow_cnt integer,
    version bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (business_unit, trip_id)
);

CREATE TABLE IF NOT EXISTS moveiq.situation (
    id uuid PRIMARY KEY,
    correlation_key varchar(512) NOT NULL,
    business_unit varchar(64) NOT NULL,
    situation_type varchar(64) NOT NULL,
    office varchar(160),
    shift varchar(32),
    direction varchar(16),
    status varchar(32) NOT NULL,
    priority varchar(16) NOT NULL,
    affected_employees bigint NOT NULL DEFAULT 0,
    delay_minutes bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uk_situation_correlation UNIQUE (correlation_key)
);

CREATE TABLE IF NOT EXISTS moveiq.situation_contribution (
    id uuid PRIMARY KEY,
    situation_id uuid NOT NULL REFERENCES moveiq.situation(id),
    source_event_id varchar(160) NOT NULL,
    affected_employees bigint NOT NULL DEFAULT 0,
    delay_minutes bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    CONSTRAINT uk_situation_source UNIQUE (situation_id, source_event_id)
);

CREATE TABLE IF NOT EXISTS moveiq.action_proposal (
    id uuid PRIMARY KEY,
    situation_id uuid NOT NULL REFERENCES moveiq.situation(id),
    action_type varchar(80) NOT NULL,
    status varchar(32) NOT NULL,
    evidence_hash varchar(128) NOT NULL,
    approved_by varchar(160),
    approved_at timestamptz,
    version bigint NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS moveiq.action_execution (
    id uuid PRIMARY KEY,
    proposal_id uuid NOT NULL REFERENCES moveiq.action_proposal(id),
    idempotency_key varchar(200) NOT NULL,
    status varchar(32) NOT NULL,
    external_reference varchar(255),
    executed_at timestamptz NOT NULL,
    CONSTRAINT uk_action_idempotency UNIQUE (idempotency_key)
);

CREATE TABLE IF NOT EXISTS moveiq.outbox_event (
    id uuid PRIMARY KEY,
    aggregate_type varchar(80) NOT NULL,
    aggregate_id varchar(160) NOT NULL,
    event_type varchar(100) NOT NULL,
    payload text NOT NULL,
    created_at timestamptz NOT NULL,
    published_at timestamptz
);

CREATE INDEX IF NOT EXISTS ix_trip_date_scope ON moveiq.trip (trip_date, business_unit, office, shift_type, trip_direction);
CREATE INDEX IF NOT EXISTS ix_situation_scope ON moveiq.situation (business_unit, status, priority, updated_at DESC);
CREATE INDEX IF NOT EXISTS ix_outbox_pending ON moveiq.outbox_event (created_at) WHERE published_at IS NULL;
