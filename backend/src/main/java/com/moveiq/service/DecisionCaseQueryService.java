package com.moveiq.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DecisionCaseQueryService {

    private final JdbcTemplate jdbc;

    public DecisionCaseQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Optional<CaseRow> byId(UUID decisionId) {

        return jdbc.query(
                        """
                        SELECT
                            id,
                            replay_session_id,
                            scope_key,
                            trigger_event_id,
                            situation_id,
                            status,
                            opened_event_time,
                            detected_event_time,
                            closed_event_time,
                            created_at,
                            updated_at
                        FROM moveiq.decision_case
                        WHERE id = ?
                        """,
                        this::map,
                        decisionId)
                .stream()
                .findFirst();
    }

    /**
     * Returns the best current operational case for the control room.
     *
     * <p>A case that has crossed detection is preferred over unrelated
     * one-event SENSING cases. Within that category, the most recently
     * updated case wins.
     */
    @Transactional(readOnly = true)
    public Optional<CaseRow> current() {

        return jdbc.query(
                        """
                        SELECT
                            id,
                            replay_session_id,
                            scope_key,
                            trigger_event_id,
                            situation_id,
                            status,
                            opened_event_time,
                            detected_event_time,
                            closed_event_time,
                            created_at,
                            updated_at
                        FROM moveiq.decision_case
                        ORDER BY
                            CASE
                                WHEN status = 'SENSING' THEN 1
                                ELSE 0
                            END,
                            updated_at DESC
                        LIMIT 1
                        """,
                        this::map)
                .stream()
                .findFirst();
    }

    private CaseRow map(
            ResultSet rs,
            int rowNum)
            throws SQLException {

        return new CaseRow(
                rs.getObject(
                        "id",
                        UUID.class),

                rs.getObject(
                        "replay_session_id",
                        UUID.class),

                rs.getString(
                        "scope_key"),

                rs.getString(
                        "trigger_event_id"),

                rs.getObject(
                        "situation_id",
                        UUID.class),

                rs.getString(
                        "status"),

                instant(
                        rs,
                        "opened_event_time"),

                instant(
                        rs,
                        "detected_event_time"),

                instant(
                        rs,
                        "closed_event_time"),

                instant(
                        rs,
                        "created_at"),

                instant(
                        rs,
                        "updated_at"));
    }

    private Instant instant(
            ResultSet rs,
            String column)
            throws SQLException {

        var timestamp =
                rs.getTimestamp(column);

        return timestamp == null
                ? null
                : timestamp.toInstant();
    }

    public record CaseRow(
            UUID id,
            UUID replaySessionId,
            String scopeKey,
            String triggerEventId,
            UUID situationId,
            String status,
            Instant openedEventTime,
            Instant detectedEventTime,
            Instant closedEventTime,
            Instant createdAt,
            Instant updatedAt) {
    }
}