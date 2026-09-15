CREATE TABLE moveiq.verification_snapshot (
    execution_id uuid PRIMARY KEY REFERENCES moveiq.action_execution(id) ON DELETE CASCADE,
    situation_id uuid NOT NULL REFERENCES moveiq.situation(id) ON DELETE CASCADE,
    business_unit varchar(100) NOT NULL,
    office varchar(200),
    shift varchar(100),
    direction varchar(50),
    baseline_event_time timestamptz NOT NULL,
    baseline_avg_delay double precision NOT NULL,
    observed_sample_size bigint NOT NULL DEFAULT 0,
    observed_delay_sum double precision NOT NULL DEFAULT 0,
    outcome varchar(40) NOT NULL DEFAULT 'INSUFFICIENT_EVIDENCE',
    methodology_version varchar(40) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_verify_outcome CHECK (outcome IN (
        'OBSERVED_IMPROVEMENT', 'NO_MATERIAL_CHANGE', 'WORSENED', 'INSUFFICIENT_EVIDENCE'))
);

CREATE INDEX ix_verification_latest
    ON moveiq.verification_snapshot(updated_at DESC);
