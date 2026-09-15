package com.moveiq.service;

import com.moveiq.api.dto.MobilityEvent;
import com.moveiq.api.dto.ReasoningSnapshot;
import com.moveiq.api.dto.VerificationSnapshot;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VerificationService {
    private static final String METHODOLOGY = "verify-v1-observational";
    private static final long MIN_SAMPLE = 3;
    private static final double MATERIAL_CHANGE_PCT = 10.0;
    private final JdbcTemplate jdbc;

    public VerificationService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public void start(UUID executionId, ReasoningSnapshot reason) {
        jdbc.update("""
                INSERT INTO moveiq.verification_snapshot(
                    execution_id, situation_id, business_unit, office, shift, direction,
                    baseline_event_time, baseline_avg_delay, methodology_version)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (execution_id) DO NOTHING
                """,
                executionId, reason.situationId(), reason.businessUnit(), reason.office(), reason.shift(),
                reason.direction(), Timestamp.from(reason.eventTime()), reason.currentAvgDelay(), METHODOLOGY);
    }

    /** Observe only events strictly after the action evidence cutoff; replay never reads future events. */
    @Transactional
    public void observe(MobilityEvent event) {
        double delay = Math.max(event.delayMinutes(), 0);
        jdbc.update("""
                UPDATE moveiq.verification_snapshot
                SET observed_sample_size = observed_sample_size + 1,
                    observed_delay_sum = observed_delay_sum + ?,
                    outcome = CASE
                        WHEN observed_sample_size + 1 < ? THEN 'INSUFFICIENT_EVIDENCE'
                        WHEN baseline_avg_delay <= 0 THEN 'INSUFFICIENT_EVIDENCE'
                        WHEN (((observed_delay_sum + ?) / (observed_sample_size + 1)) - baseline_avg_delay)
                             / baseline_avg_delay * 100 <= -? THEN 'OBSERVED_IMPROVEMENT'
                        WHEN (((observed_delay_sum + ?) / (observed_sample_size + 1)) - baseline_avg_delay)
                             / baseline_avg_delay * 100 >= ? THEN 'WORSENED'
                        ELSE 'NO_MATERIAL_CHANGE'
                    END,
                    updated_at = now()
                WHERE business_unit = ?
                  AND (office IS NULL OR office = ?)
                  AND (shift IS NULL OR shift = ?)
                  AND (direction IS NULL OR direction = ?)
                  AND baseline_event_time < ?
                """,
                delay, MIN_SAMPLE, delay, MATERIAL_CHANGE_PCT, delay, MATERIAL_CHANGE_PCT,
                event.businessUnit(), event.office(), event.shift(), event.direction(), Timestamp.from(event.occurredAt()));
    }

    @Transactional(readOnly = true)
    public Optional<VerificationSnapshot> latest() {
        return jdbc.query("""
                SELECT execution_id, situation_id, baseline_event_time, baseline_avg_delay,
                       observed_sample_size,
                       CASE WHEN observed_sample_size = 0 THEN NULL ELSE observed_delay_sum / observed_sample_size END AS observed_avg_delay,
                       CASE WHEN observed_sample_size = 0 OR baseline_avg_delay <= 0 THEN NULL
                            ELSE ((observed_delay_sum / observed_sample_size) - baseline_avg_delay) / baseline_avg_delay * 100 END AS change_pct,
                       outcome, methodology_version, updated_at
                FROM moveiq.verification_snapshot ORDER BY updated_at DESC LIMIT 1
                """, this::map).stream().findFirst();
    }

    private VerificationSnapshot map(ResultSet rs, int rowNum) throws SQLException {
        return new VerificationSnapshot(
                rs.getObject("execution_id", UUID.class), rs.getObject("situation_id", UUID.class),
                rs.getTimestamp("baseline_event_time").toInstant(), rs.getDouble("baseline_avg_delay"),
                rs.getLong("observed_sample_size"), nullableDouble(rs, "observed_avg_delay"),
                nullableDouble(rs, "change_pct"), rs.getString("outcome"), rs.getString("methodology_version"),
                rs.getTimestamp("updated_at").toInstant());
    }

    private Double nullableDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }
}
