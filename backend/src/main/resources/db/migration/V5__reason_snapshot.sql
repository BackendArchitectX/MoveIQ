CREATE TABLE IF NOT EXISTS moveiq.reason_snapshot (
    situation_id uuid PRIMARY KEY REFERENCES moveiq.situation(id) ON DELETE CASCADE,
    business_unit varchar(64) NOT NULL,
    office varchar(160),
    shift varchar(32),
    direction varchar(16),
    event_time timestamptz NOT NULL,
    current_avg_delay double precision NOT NULL,
    baseline_avg_delay double precision,
    delta_pct double precision,
    current_sample_size bigint NOT NULL,
    baseline_sample_size bigint NOT NULL,
    coverage_pct double precision NOT NULL,
    trust_status varchar(16) NOT NULL,
    recommendation varchar(80) NOT NULL,
    methodology_version varchar(32) NOT NULL,
    computed_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_reason_snapshot_trust
        CHECK (trust_status IN ('HIGH', 'MEDIUM', 'LOW'))
);

CREATE INDEX IF NOT EXISTS ix_reason_snapshot_computed
    ON moveiq.reason_snapshot (computed_at DESC);
