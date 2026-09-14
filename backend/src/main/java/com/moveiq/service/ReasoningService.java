package com.moveiq.service;

import com.moveiq.api.dto.DetectedSignal;
import com.moveiq.api.dto.ReasoningSnapshot;
import com.moveiq.domain.SituationEntity;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReasoningService {
    private static final String METHODOLOGY_VERSION = "reason-v1";
    private static final int CURRENT_WINDOW = 25;
    private static final int BASELINE_WINDOW = 100;
    private static final String EVENT_EPOCH =
            "COALESCE(actual_start_epoch, planned_start_epoch, actual_end_epoch, planned_end_epoch)";

    private final JdbcTemplate jdbc;

    public ReasoningService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public ReasoningSnapshot computeAndStore(SituationEntity situation, DetectedSignal signal) {
        ReasonStats stats = computeStats(signal);
        String trust = trust(stats);
        String recommendation = recommendation(stats);
        double coverage = coverage(stats);
        Double deltaPct = deltaPct(stats.currentAvgDelay(), stats.baselineAvgDelay());
        Instant computedAt = Instant.now();

        jdbc.update("""
                INSERT INTO moveiq.reason_snapshot(
                    situation_id, business_unit, office, shift, direction, event_time,
                    current_avg_delay, baseline_avg_delay, delta_pct,
                    current_sample_size, baseline_sample_size, coverage_pct,
                    trust_status, recommendation, methodology_version, computed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (situation_id) DO UPDATE SET
                    business_unit = EXCLUDED.business_unit,
                    office = EXCLUDED.office,
                    shift = EXCLUDED.shift,
                    direction = EXCLUDED.direction,
                    event_time = EXCLUDED.event_time,
                    current_avg_delay = EXCLUDED.current_avg_delay,
                    baseline_avg_delay = EXCLUDED.baseline_avg_delay,
                    delta_pct = EXCLUDED.delta_pct,
                    current_sample_size = EXCLUDED.current_sample_size,
                    baseline_sample_size = EXCLUDED.baseline_sample_size,
                    coverage_pct = EXCLUDED.coverage_pct,
                    trust_status = EXCLUDED.trust_status,
                    recommendation = EXCLUDED.recommendation,
                    methodology_version = EXCLUDED.methodology_version,
                    computed_at = EXCLUDED.computed_at
                """,
                situation.getId(), signal.businessUnit(), signal.office(), signal.shift(), signal.direction(),
                Timestamp.from(signal.detectedAt()), stats.currentAvgDelay(), stats.baselineAvgDelay(), deltaPct,
                stats.currentCount(), stats.baselineCount(), coverage, trust, recommendation,
                METHODOLOGY_VERSION, Timestamp.from(computedAt));

        return new ReasoningSnapshot(
                situation.getId(), signal.businessUnit(), signal.office(), signal.shift(), signal.direction(),
                signal.detectedAt(), stats.currentAvgDelay(), stats.baselineAvgDelay(), deltaPct,
                stats.currentCount(), stats.baselineCount(), coverage, trust, recommendation,
                METHODOLOGY_VERSION, computedAt);
    }

    @Transactional(readOnly = true)
    public Optional<ReasoningSnapshot> latest() {
        return jdbc.query("""
                SELECT situation_id, business_unit, office, shift, direction, event_time,
                       current_avg_delay, baseline_avg_delay, delta_pct,
                       current_sample_size, baseline_sample_size, coverage_pct,
                       trust_status, recommendation, methodology_version, computed_at
                FROM moveiq.reason_snapshot
                ORDER BY computed_at DESC
                LIMIT 1
                """, this::map).stream().findFirst();
    }

    private ReasonStats computeStats(DetectedSignal signal) {
        long cutoff = signal.detectedAt().getEpochSecond();
        String sql = """
                WITH scoped AS (
                    SELECT
                        GREATEST(COALESCE(
                            reported_delay_minutes,
                            CASE WHEN planned_end_epoch IS NOT NULL AND actual_end_epoch IS NOT NULL
                                 THEN GREATEST((actual_end_epoch - planned_end_epoch) / 60, 0)
                            END,
                            0), 0)::double precision AS delay_minutes,
                        CASE WHEN reported_delay_minutes IS NOT NULL
                                  OR (planned_end_epoch IS NOT NULL AND actual_end_epoch IS NOT NULL)
                             THEN 1 ELSE 0 END AS covered,
                        ROW_NUMBER() OVER (
                            ORDER BY %s DESC, trip_id DESC) AS rn
                    FROM moveiq.trip
                    WHERE business_unit = ?
                      AND (? IS NULL OR office = ?)
                      AND (? IS NULL OR shift_type = ?)
                      AND (? IS NULL OR trip_direction = ?)
                      AND %s IS NOT NULL
                      AND %s <= ?
                )
                SELECT
                    COALESCE(AVG(delay_minutes) FILTER (WHERE rn <= ?), 0),
                    AVG(delay_minutes) FILTER (WHERE rn > ? AND rn <= ?),
                    COUNT(*) FILTER (WHERE rn <= ?),
                    COUNT(*) FILTER (WHERE rn > ? AND rn <= ?),
                    COALESCE(SUM(covered) FILTER (WHERE rn <= ?), 0)
                FROM scoped
                """.formatted(EVENT_EPOCH, EVENT_EPOCH, EVENT_EPOCH);

        return jdbc.queryForObject(
                sql,
                (rs, rowNum) -> new ReasonStats(
                        rs.getDouble(1), nullableDouble(rs, 2), rs.getLong(3), rs.getLong(4), rs.getLong(5)),
                signal.businessUnit(),
                signal.office(), signal.office(),
                signal.shift(), signal.shift(),
                signal.direction(), signal.direction(),
                cutoff,
                CURRENT_WINDOW,
                CURRENT_WINDOW, CURRENT_WINDOW + BASELINE_WINDOW,
                CURRENT_WINDOW,
                CURRENT_WINDOW, CURRENT_WINDOW + BASELINE_WINDOW,
                CURRENT_WINDOW);
    }

    private ReasoningSnapshot map(ResultSet rs, int rowNum) throws SQLException {
        return new ReasoningSnapshot(
                rs.getObject("situation_id", UUID.class), rs.getString("business_unit"), rs.getString("office"),
                rs.getString("shift"), rs.getString("direction"), rs.getTimestamp("event_time").toInstant(),
                rs.getDouble("current_avg_delay"), nullableDouble(rs, "baseline_avg_delay"),
                nullableDouble(rs, "delta_pct"), rs.getLong("current_sample_size"),
                rs.getLong("baseline_sample_size"), rs.getDouble("coverage_pct"), rs.getString("trust_status"),
                rs.getString("recommendation"), rs.getString("methodology_version"),
                rs.getTimestamp("computed_at").toInstant());
    }

    private String trust(ReasonStats stats) {
        double coverage = coverage(stats);
        if (stats.currentCount() >= 10 && stats.baselineCount() >= 25 && coverage >= 80.0) return "HIGH";
        if (stats.currentCount() >= 3 && stats.baselineCount() >= 10 && coverage >= 50.0) return "MEDIUM";
        return "LOW";
    }

    private String recommendation(ReasonStats stats) {
        double delta = Optional.ofNullable(deltaPct(stats.currentAvgDelay(), stats.baselineAvgDelay())).orElse(0.0);
        if (stats.currentAvgDelay() >= 20.0 && delta >= 25.0) return "ESCALATE_VENDOR_AND_CAPACITY";
        if (stats.currentAvgDelay() >= 10.0 && delta >= 15.0) return "INVESTIGATE_VENDOR";
        return "MONITOR";
    }

    private double coverage(ReasonStats stats) {
        if (stats.currentCount() == 0) return 0.0;
        return (stats.coveredCurrentCount() * 100.0) / stats.currentCount();
    }

    private Double deltaPct(double current, Double baseline) {
        if (baseline == null || baseline <= 0.0) return null;
        return ((current - baseline) / baseline) * 100.0;
    }

    private Double nullableDouble(ResultSet rs, int index) throws SQLException {
        double value = rs.getDouble(index);
        return rs.wasNull() ? null : value;
    }

    private Double nullableDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }

    private record ReasonStats(
            double currentAvgDelay,
            Double baselineAvgDelay,
            long currentCount,
            long baselineCount,
            long coveredCurrentCount) {}
}
