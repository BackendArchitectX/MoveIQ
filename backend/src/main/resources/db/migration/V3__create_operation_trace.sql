CREATE TABLE IF NOT EXISTS moveiq.operation_trace (
                                                      sequence bigserial PRIMARY KEY,
                                                      session_id uuid,
                                                      situation_id uuid REFERENCES moveiq.situation(id) ON DELETE SET NULL,
    source_event_id varchar(200),
    scope_key varchar(512),
    stage varchar(16) NOT NULL,
    event_type varchar(100) NOT NULL,
    event_time timestamptz NOT NULL,
    recorded_at timestamptz NOT NULL DEFAULT now(),
    summary varchar(600) NOT NULL,
    evidence jsonb NOT NULL DEFAULT '{}'::jsonb,

    CONSTRAINT ck_operation_trace_stage
    CHECK (stage IN ('SENSE', 'REASON', 'ACT', 'VERIFY'))
    );

CREATE INDEX IF NOT EXISTS ix_operation_trace_recorded
    ON moveiq.operation_trace (recorded_at DESC);

CREATE INDEX IF NOT EXISTS ix_operation_trace_situation
    ON moveiq.operation_trace (situation_id, sequence);