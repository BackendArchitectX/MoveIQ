CREATE TABLE IF NOT EXISTS moveiq.processed_event (
    consumer_group varchar(160) NOT NULL,
    business_unit varchar(64) NOT NULL,
    event_id varchar(200) NOT NULL,
    processed_at timestamptz NOT NULL,
    PRIMARY KEY (consumer_group, business_unit, event_id)
);

CREATE INDEX IF NOT EXISTS ix_processed_event_time
    ON moveiq.processed_event (processed_at DESC);

ALTER TABLE moveiq.action_execution
    ADD CONSTRAINT uk_action_execution_proposal UNIQUE (proposal_id);

CREATE INDEX IF NOT EXISTS ix_contribution_situation_source
    ON moveiq.situation_contribution (situation_id, source_event_id);
