package com.moveiq.store;

import com.moveiq.api.dto.DetectedSignal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SituationContributionStore {
    private final JdbcTemplate jdbc;

    public SituationContributionStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean insertIfAbsent(UUID situationId, DetectedSignal signal) {
        int changed = jdbc.update(
                """
                INSERT INTO moveiq.situation_contribution(
                    id, situation_id, source_event_id, affected_employees, delay_minutes, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (situation_id, source_event_id) DO NOTHING
                """,
                UUID.randomUUID(),
                situationId,
                signal.sourceEventId(),
                signal.affectedEmployees(),
                signal.delayMinutes(),
                Instant.now());
        return changed == 1;
    }
}
