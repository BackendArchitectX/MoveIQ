CREATE TABLE IF NOT EXISTS moveiq.decision_case (
                                                    id uuid PRIMARY KEY,

                                                    replay_session_id uuid,

                                                    scope_key varchar(512) NOT NULL,

    trigger_event_id varchar(200),

    situation_id uuid
    REFERENCES moveiq.situation(id)
    ON DELETE SET NULL,

    status varchar(32) NOT NULL,

    opened_event_time timestamptz NOT NULL,

    detected_event_time timestamptz,

    closed_event_time timestamptz,

    created_at timestamptz NOT NULL DEFAULT now(),

    updated_at timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT ck_decision_case_status
    CHECK (
              status IN (
              'SENSING',
              'DETECTED',
              'REASONING',
              'MONITORING',
              'ACTION_PLANNED',
              'AWAITING_APPROVAL',
              'EXECUTING',
              'VERIFYING',
              'RESOLVED'
                        )
    )
    );

ALTER TABLE moveiq.operation_trace
    ADD COLUMN IF NOT EXISTS decision_id uuid
    REFERENCES moveiq.decision_case(id)
    ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS ix_operation_trace_decision
    ON moveiq.operation_trace(decision_id, sequence);

CREATE INDEX IF NOT EXISTS ix_decision_case_scope_status
    ON moveiq.decision_case(scope_key, status);

CREATE INDEX IF NOT EXISTS ix_decision_case_situation
    ON moveiq.decision_case(situation_id, updated_at DESC);

CREATE INDEX IF NOT EXISTS ix_decision_case_created
    ON moveiq.decision_case(created_at DESC);