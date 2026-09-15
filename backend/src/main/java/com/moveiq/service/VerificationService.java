package com.moveiq.service;

import com.moveiq.api.dto.OperationTraceEvent;
import com.moveiq.api.dto.VerificationSnapshot;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
public class VerificationService {
    private static final String METHODOLOGY = "verify-v1-observational";
    private static final long MIN_SAMPLE = 3;
    private static final double MATERIAL_CHANGE_PCT = 10.0;
    private final JdbcTemplate jdbc;

    public VerificationService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** Consume only committed trace events; REQUIRES_NEW makes projection writes durable after source commit. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onTrace(OperationTraceEvent trace) {
        if ("ACTION_EXECUTED".equals(trace.eventType()) && trace.situationId() != null) {
            Object executionId = trace.evidence().get("executionId");
            if (executionId != null) start(UUID.fromString(executionId.toString()), trace.situationId());
            return;
        }
        if (!"EVENT_ACCEPTED".equals(trace.eventType()) || trace.scopeKey() == null) return;
        Object delayValue = trace.evidence().get("delayMinutes");
        if (!(delayValue instanceof Number delay)) return;
        String[] scope = trace.scopeKey().split("\\|", -1);
        if (scope.length < 4) return;
        observe(scope[0], nullableScope(scope[1]), nullableScope(scope[2]), nullableScope(scope[3]),
                trace.eventTime(), Math.max(delay.doubleValue(), 0));
    }

    private void start(UUID executionId, UUID situationId) {
        jdbc.update("""
                INSERT INTO moveiq.verification_snapshot(
                    execution_id, situation_id, business_unit, office, shift, direction,
                    baseline_event_time, baseline_avg_delay, methodology_version)
                SELECT ?, situation_id, business_unit, office, shift, direction,
                       event_time, current_avg_delay, ?
                FROM moveiq.reason_snapshot WHERE situation_id = ?
                ON CONFLICT (execution_id) DO NOTHING
                """, executionId, METHODOLOGY, situationId);
    }

    private void observe(String businessUnit, String office, String shift, String direction,
                         java.time.Instant eventTime, double delay) {
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
                businessUnit, office, shift, direction, Timestamp.from(eventTime));
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

    private String nullableScope(String value) { return "_".equals(value) ? null : value; }
    private Double nullableDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }
}
