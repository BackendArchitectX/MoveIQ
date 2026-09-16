package com.moveiq.service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DecisionCaseService {

    private final JdbcTemplate jdbc;

    public DecisionCaseService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Persist the Redis-owned decision episode.
     *
     * <p>The same decisionId may arrive repeatedly because multiple events
     * belong to the same active Redis window. The insert is therefore
     * deliberately idempotent.
     *
     * <p>Never overwrite lifecycle state here. A later 2/3 or 3/3 event
     * must not move an already DETECTED/REASONING case backwards to SENSING.
     */
    @Transactional
    public void ensureSensing(
            UUID decisionId,
            UUID replaySessionId,
            String scopeKey,
            Instant openedEventTime) {

        if (decisionId == null) {
            return;
        }

        jdbc.update(
                """
                INSERT INTO moveiq.decision_case(
                    id,
                    replay_session_id,
                    scope_key,
                    status,
                    opened_event_time,
                    created_at,
                    updated_at
                )
                VALUES (?, ?, ?, 'SENSING', ?, now(), now())
                ON CONFLICT (id) DO UPDATE SET
                    updated_at = now()
                """,
                decisionId,
                replaySessionId,
                scopeKey,
                Timestamp.from(openedEventTime));
    }

    /**
     * Mark the exact event that crossed the deterministic detection threshold.
     */
    @Transactional
    public void markDetected(
            UUID decisionId,
            String triggerEventId,
            Instant detectedEventTime) {

        requireDecisionId(decisionId);

        int updated = jdbc.update(
                """
                UPDATE moveiq.decision_case
                SET trigger_event_id = COALESCE(trigger_event_id, ?),
                    detected_event_time = COALESCE(detected_event_time, ?),
                    status = CASE
                        WHEN status = 'SENSING'
                            THEN 'DETECTED'
                        ELSE status
                    END,
                    updated_at = now()
                WHERE id = ?
                """,
                triggerEventId,
                Timestamp.from(detectedEventTime),
                decisionId);

        requireExistingCase(updated, decisionId);
    }

    /**
     * Connect the distributed detection episode to the durable Situation
     * and explicitly enter the reasoning phase.
     */
    @Transactional
    public void attachSituationAndStartReasoning(
            UUID decisionId,
            UUID situationId) {

        requireDecisionId(decisionId);

        int updated = jdbc.update(
                """
                UPDATE moveiq.decision_case
                SET situation_id = ?,
                    status = CASE
                        WHEN status IN (
                            'SENSING',
                            'DETECTED',
                            'REASONING'
                        )
                            THEN 'REASONING'
                        ELSE status
                    END,
                    updated_at = now()
                WHERE id = ?
                """,
                situationId,
                decisionId);

        requireExistingCase(updated, decisionId);
    }

    /**
     * Complete deterministic reasoning.
     *
     * <p>MONITOR means there is currently insufficient business evidence
     * to justify an intervention. Any stronger recommendation becomes
     * ACTION_PLANNED and will later be handled by the autonomous policy
     * layer.
     */
    @Transactional
    public void markReasoned(
            UUID decisionId,
            String recommendation) {

        requireDecisionId(decisionId);

        String nextStatus =
                "MONITOR".equals(recommendation)
                        ? "MONITORING"
                        : "ACTION_PLANNED";

        int updated = jdbc.update(
                """
                UPDATE moveiq.decision_case
                SET status = CASE
                        WHEN status IN (
                            'DETECTED',
                            'REASONING'
                        )
                            THEN ?
                        ELSE status
                    END,
                    updated_at = now()
                WHERE id = ?
                """,
                nextStatus,
                decisionId);

        requireExistingCase(updated, decisionId);
    }

    @Transactional
    public void markMonitoring(
            UUID decisionId) {

        transition(
                decisionId,
                "MONITORING",
                "ACTION_PLANNED",
                "MONITORING");
    }
    /**
     * Atomically claim a monitoring decision for one new reasoning pass.
     *
     * <p>Only one concurrent qualifying observation may move the case from
     * MONITORING back to REASONING.
     */
    @Transactional
    public Optional<UUID> claimMonitoringForReevaluation(
            UUID decisionId) {

        requireDecisionId(decisionId);

        return jdbc.query(
                        """
                        UPDATE moveiq.decision_case
                        SET status = 'REASONING',
                            updated_at = now()
                        WHERE id = ?
                          AND status = 'MONITORING'
                          AND situation_id IS NOT NULL
                        RETURNING situation_id
                        """,
                        (rs, rowNum) ->
                                rs.getObject(
                                        "situation_id",
                                        UUID.class),
                        decisionId)
                .stream()
                .findFirst();
    }


    @Transactional
    public void markAwaitingApproval(UUID decisionId) {

        transition(
                decisionId,
                "AWAITING_APPROVAL",
                "ACTION_PLANNED");
    }

    @Transactional
    public void markExecuting(UUID decisionId) {

        transition(
                decisionId,
                "EXECUTING",
                "ACTION_PLANNED",
                "AWAITING_APPROVAL");
    }

    @Transactional
    public void markVerifying(UUID decisionId) {

        transition(
                decisionId,
                "VERIFYING",
                "EXECUTING",
                "AWAITING_APPROVAL",
                "ACTION_PLANNED");
    }

    @Transactional
    public void markResolved(
            UUID decisionId,
            Instant closedEventTime) {

        requireDecisionId(decisionId);

        int updated = jdbc.update(
                """
                UPDATE moveiq.decision_case
                SET status = 'RESOLVED',
                    closed_event_time = ?,
                    updated_at = now()
                WHERE id = ?
                """,
                Timestamp.from(closedEventTime),
                decisionId);

        requireExistingCase(updated, decisionId);
    }

    @Transactional(readOnly = true)
    public Optional<UUID> latestDecisionForSituation(
            UUID situationId) {

        return jdbc.query(
                        """
                        SELECT id
                        FROM moveiq.decision_case
                        WHERE situation_id = ?
                        ORDER BY
                            detected_event_time DESC NULLS LAST,
                            created_at DESC
                        LIMIT 1
                        """,
                        (rs, rowNum) ->
                                rs.getObject(
                                        "id",
                                        UUID.class),
                        situationId)
                .stream()
                .findFirst();
    }

    private void transition(
            UUID decisionId,
            String target,
            String... allowedCurrentStates) {

        requireDecisionId(decisionId);

        if (allowedCurrentStates.length == 0) {
            throw new IllegalArgumentException(
                    "At least one source status is required");
        }

        StringBuilder sql =
                new StringBuilder(
                        """
                        UPDATE moveiq.decision_case
                        SET status = ?,
                            updated_at = now()
                        WHERE id = ?
                          AND status IN (
                        """);

        for (int i = 0; i < allowedCurrentStates.length; i++) {
            if (i > 0) {
                sql.append(", ");
            }

            sql.append("?");
        }

        sql.append(")");

        Object[] args =
                new Object[2 + allowedCurrentStates.length];

        args[0] = target;
        args[1] = decisionId;

        System.arraycopy(
                allowedCurrentStates,
                0,
                args,
                2,
                allowedCurrentStates.length);

        jdbc.update(
                sql.toString(),
                args);
    }

    private void requireDecisionId(UUID decisionId) {

        if (decisionId == null) {
            throw new IllegalArgumentException(
                    "decisionId is required");
        }
    }

    private void requireExistingCase(
            int updated,
            UUID decisionId) {

        if (updated == 0) {
            throw new IllegalStateException(
                    "Decision case does not exist: "
                            + decisionId);
        }
    }
}