package com.moveiq.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moveiq.api.dto.OperationTraceEvent;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class OperationTraceQueryService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public OperationTraceQueryService(
            JdbcTemplate jdbc,
            ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    /**
     * Global durable stream used for SSE reconnect/catch-up.
     */
    public List<OperationTraceEvent> after(
            long sequence,
            int limit) {

        int boundedLimit =
                Math.max(
                        1,
                        Math.min(
                                limit,
                                500));

        return queryAfter(
                sequence,
                Long.MAX_VALUE,
                boundedLimit);
    }

    public long latestSequence() {
        Long latest =
                jdbc.queryForObject(
                        "SELECT COALESCE(MAX(sequence), 0) FROM moveiq.operation_trace",
                        Long.class);

        return latest == null ? 0L : latest;
    }

    public List<OperationTraceEvent> afterThrough(
            long sequence,
            long through,
            int limit) {

        int boundedLimit =
                Math.max(
                        1,
                        Math.min(
                                limit,
                                500));

        return queryAfter(
                sequence,
                through,
                boundedLimit);
    }

    private List<OperationTraceEvent> queryAfter(
            long sequence,
            long through,
            int limit) {

        return jdbc.query(
                """
                SELECT
                    sequence,
                    session_id,
                    decision_id,
                    situation_id,
                    source_event_id,
                    scope_key,
                    stage,
                    event_type,
                    event_time,
                    recorded_at,
                    summary,
                    evidence
                FROM moveiq.operation_trace
                WHERE sequence > ? AND sequence <= ?
                ORDER BY sequence
                LIMIT ?
                """,
                this::map,
                sequence,
                through,
                limit);
    }

    /**
     * Ordered proof ledger for one decision case.
     */
    public List<OperationTraceEvent> forDecision(
            UUID decisionId,
            int limit) {

        int boundedLimit =
                Math.max(
                        1,
                        Math.min(
                                limit,
                                1000));

        return jdbc.query(
                """
                SELECT
                    sequence,
                    session_id,
                    decision_id,
                    situation_id,
                    source_event_id,
                    scope_key,
                    stage,
                    event_type,
                    event_time,
                    recorded_at,
                    summary,
                    evidence
                FROM moveiq.operation_trace
                WHERE decision_id = ?
                ORDER BY sequence
                LIMIT ?
                """,
                this::map,
                decisionId,
                boundedLimit);
    }

    private OperationTraceEvent map(
            ResultSet rs,
            int rowNum)
            throws SQLException {

        Map<String, Object> evidence;

        try {
            evidence =
                    objectMapper.readValue(
                            rs.getString("evidence"),
                            new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new SQLException(
                    "Unable to deserialize operation trace evidence",
                    e);
        }

        return new OperationTraceEvent(
                rs.getLong("sequence"),
                rs.getObject("session_id", UUID.class),
                rs.getObject("decision_id", UUID.class),
                rs.getObject("situation_id", UUID.class),
                rs.getString("source_event_id"),
                rs.getString("scope_key"),
                rs.getString("stage"),
                rs.getString("event_type"),
                rs.getTimestamp("event_time").toInstant(),
                rs.getTimestamp("recorded_at").toInstant(),
                rs.getString("summary"),
                evidence);
    }
}