package com.moveiq.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moveiq.api.dto.OperationTraceEvent;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class OperationTraceService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher events;

    public OperationTraceService(
            JdbcTemplate jdbc,
            ObjectMapper objectMapper,
            ApplicationEventPublisher events) {

        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.events = events;
    }

    /**
     * Backward-compatible trace method.
     *
     * <p>Existing ACT/VERIFY callers can continue compiling while
     * decision correlation is introduced incrementally.
     */
    public OperationTraceEvent record(
            UUID sessionId,
            UUID situationId,
            String sourceEventId,
            String scopeKey,
            String stage,
            String eventType,
            Instant eventTime,
            String summary,
            Map<String, Object> evidence) {

        return record(
                sessionId,
                null,
                situationId,
                sourceEventId,
                scopeKey,
                stage,
                eventType,
                eventTime,
                summary,
                evidence);
    }

    /**
     * Decision-correlated trace method.
     */
    public OperationTraceEvent record(
            UUID sessionId,
            UUID decisionId,
            UUID situationId,
            String sourceEventId,
            String scopeKey,
            String stage,
            String eventType,
            Instant eventTime,
            String summary,
            Map<String, Object> evidence) {

        Map<String, Object> safeEvidence =
                evidence == null
                        ? Map.of()
                        : Map.copyOf(evidence);

        String evidenceJson;

        try {
            evidenceJson =
                    objectMapper.writeValueAsString(
                            safeEvidence);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Unable to serialize trace evidence",
                    e);
        }

        OperationTraceEvent trace =
                jdbc.queryForObject(
                        """
                        INSERT INTO moveiq.operation_trace(
                            session_id,
                            decision_id,
                            situation_id,
                            source_event_id,
                            scope_key,
                            stage,
                            event_type,
                            event_time,
                            summary,
                            evidence
                        )
                        VALUES (
                            ?, ?, ?, ?, ?, ?, ?, ?, ?,
                            CAST(? AS jsonb)
                        )
                        RETURNING
                            sequence,
                            recorded_at
                        """,
                        (rs, rowNum) ->
                                new OperationTraceEvent(
                                        rs.getLong(
                                                "sequence"),
                                        sessionId,
                                        decisionId,
                                        situationId,
                                        sourceEventId,
                                        scopeKey,
                                        stage,
                                        eventType,
                                        eventTime,
                                        rs.getTimestamp(
                                                        "recorded_at")
                                                .toInstant(),
                                        summary,
                                        safeEvidence),
                        sessionId,
                        decisionId,
                        situationId,
                        sourceEventId,
                        scopeKey,
                        stage,
                        eventType,
                        Timestamp.from(eventTime),
                        summary,
                        evidenceJson);

        events.publishEvent(trace);

        return trace;
    }
}