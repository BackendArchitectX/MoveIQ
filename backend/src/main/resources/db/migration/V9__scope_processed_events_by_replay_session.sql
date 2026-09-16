ALTER TABLE moveiq.processed_event
    ADD COLUMN replay_session_id uuid;

ALTER TABLE moveiq.processed_event
DROP CONSTRAINT IF EXISTS processed_event_pkey;

CREATE UNIQUE INDEX ux_processed_event_live
    ON moveiq.processed_event (
                               consumer_group,
                               business_unit,
                               event_id
        )
    WHERE replay_session_id IS NULL;

CREATE UNIQUE INDEX ux_processed_event_replay
    ON moveiq.processed_event (
                               consumer_group,
                               replay_session_id,
                               business_unit,
                               event_id
        )
    WHERE replay_session_id IS NOT NULL;

CREATE INDEX ix_processed_event_replay_time
    ON moveiq.processed_event (
                               replay_session_id,
                               processed_at DESC
        )
    WHERE replay_session_id IS NOT NULL;