package com.moveiq.store;

import com.moveiq.api.dto.DetectedSignal;
import com.moveiq.domain.SituationEntity;
import com.moveiq.repository.SituationRepository;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SituationStore {
    private final JdbcTemplate jdbc;
    private final SituationRepository situations;

    public SituationStore(JdbcTemplate jdbc, SituationRepository situations) {
        this.jdbc = jdbc;
        this.situations = situations;
    }

    public SituationEntity getOrCreate(String correlationKey, DetectedSignal signal) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update(
                """
                INSERT INTO moveiq.situation(
                    id, correlation_key, business_unit, situation_type, office, shift, direction,
                    status, priority, affected_employees, delay_minutes, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'DETECTED', 'MEDIUM', 0, 0, ?, ?, 0)
                ON CONFLICT (correlation_key) DO NOTHING
                """,
                id,
                correlationKey,
                signal.businessUnit(),
                signal.signalType(),
                signal.office(),
                signal.shift(),
                signal.direction(),
                now,
                now);
        return situations.findByCorrelationKey(correlationKey)
                .orElseThrow(() -> new IllegalStateException("Situation upsert did not produce a row"));
    }
}
