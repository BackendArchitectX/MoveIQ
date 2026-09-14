CREATE INDEX IF NOT EXISTS ix_trip_replay_event_order
    ON moveiq.trip (
        (COALESCE(actual_start_epoch, planned_start_epoch, actual_end_epoch, planned_end_epoch)),
        business_unit,
        trip_id
    )
    WHERE COALESCE(actual_start_epoch, planned_start_epoch, actual_end_epoch, planned_end_epoch) IS NOT NULL;
