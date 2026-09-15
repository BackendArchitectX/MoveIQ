CREATE TABLE moveiq.verification_observation (
    execution_id uuid NOT NULL REFERENCES moveiq.verification_snapshot(execution_id) ON DELETE CASCADE,
    source_event_id varchar(200) NOT NULL,
    event_time timestamptz NOT NULL,
    delay_minutes double precision NOT NULL,
    recorded_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (execution_id, source_event_id)
);

CREATE INDEX ix_verification_observation_event_time
    ON moveiq.verification_observation(execution_id, event_time);
