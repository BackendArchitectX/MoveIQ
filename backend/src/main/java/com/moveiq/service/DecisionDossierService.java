package com.moveiq.service;

import com.moveiq.api.dto.DecisionDossier;
import com.moveiq.api.dto.OperationTraceEvent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DecisionDossierService {

    private static final int MAX_PROOF_EVENTS = 1000;

    private final DecisionCaseQueryService cases;
    private final OperationTraceQueryService traces;

    public DecisionDossierService(
            DecisionCaseQueryService cases,
            OperationTraceQueryService traces) {

        this.cases = cases;
        this.traces = traces;
    }

    public Optional<DecisionDossier> current() {

        return cases.current()
                .map(this::build);
    }

    public Optional<DecisionDossier> byId(
            UUID decisionId) {

        return cases.byId(decisionId)
                .map(this::build);
    }

    private DecisionDossier build(
            DecisionCaseQueryService.CaseRow row) {

        List<OperationTraceEvent> proof =
                List.copyOf(
                        traces.forDecision(
                                row.id(),
                                MAX_PROOF_EVENTS));

        DecisionDossier.DecisionCase caseInfo =
                new DecisionDossier.DecisionCase(
                        row.id(),
                        row.replaySessionId(),
                        row.status(),
                        row.scopeKey(),
                        row.triggerEventId(),
                        row.situationId(),
                        row.openedEventTime(),
                        row.detectedEventTime(),
                        row.closedEventTime(),
                        row.createdAt(),
                        row.updatedAt());

        DecisionDossier.Sense sense =
                sense(
                        row,
                        proof);

        DecisionDossier.Reason reason =
                reason(
                        row,
                        proof);

        DecisionDossier.Act act =
                act(
                        row,
                        proof);

        DecisionDossier.Verify verify =
                verify(
                        row,
                        proof);

        Long latestSequence =
                proof.isEmpty()
                        ? null
                        : proof.get(proof.size() - 1)
                        .sequence();

        return new DecisionDossier(
                caseInfo,
                sense,
                reason,
                act,
                verify,
                proof,
                latestSequence,
                Instant.now());
    }

    private DecisionDossier.Sense sense(
            DecisionCaseQueryService.CaseRow row,
            List<OperationTraceEvent> proof) {

        OperationTraceEvent latestWindow =
                latestEvent(
                        proof,
                        "SENSE",
                        "WINDOW_UPDATED");

        OperationTraceEvent signal =
                latestEvent(
                        proof,
                        "SENSE",
                        "SIGNAL_DETECTED");

        OperationTraceEvent crossingWindow =
                thresholdCrossingWindow(
                        proof);

        Map<String, Object> latestEvidence =
                evidence(latestWindow);

        Map<String, Object> signalEvidence =
                evidence(signal);

        Map<String, Object> crossingEvidence =
                evidence(crossingWindow);

        Integer latestWindowCount =
                integer(
                        latestEvidence.get(
                                "windowEventCount"));

        Integer detectedWindowCount =
                integer(
                        signalEvidence.get(
                                "windowEventCount"));

        Integer threshold =
                integer(
                        crossingEvidence.get(
                                "threshold"));

        if (threshold == null) {
            threshold =
                    integer(
                            latestEvidence.get(
                                    "threshold"));
        }

        Long affectedEmployeeTrips;

        if (signal != null) {
            affectedEmployeeTrips =
                    longValue(
                            signalEvidence.get(
                                    "affectedEmployeeTrips"));
        } else {
            affectedEmployeeTrips =
                    longValue(
                            latestEvidence.get(
                                    "affectedEmployeeTrips"));
        }

        Long delayMinutesSum;

        if (signal != null) {
            delayMinutesSum =
                    longValue(
                            signalEvidence.get(
                                    "delayMinutesSum"));
        } else {
            delayMinutesSum =
                    longValue(
                            latestEvidence.get(
                                    "delayMinutesSum"));
        }

        Double averageDelay =
                decimal(
                        crossingEvidence.get(
                                "averageDelayMinutes"));

        if (averageDelay == null) {
            averageDelay =
                    decimal(
                            latestEvidence.get(
                                    "averageDelayMinutes"));
        }

        Long watermark =
                longValue(
                        latestEvidence.get(
                                "watermarkEpochMillis"));

        List<String> sourceEventIds =
                sourceEventIds(
                        proof);

        String state;

        if (signal != null) {
            state = "DETECTED";
        } else if (!"SENSING".equals(row.status())) {
            state = "MISSING_PROOF";
        } else {
            state = "SENSING";
        }

        return new DecisionDossier.Sense(
                state,
                latestWindowCount,
                detectedWindowCount,
                threshold,
                affectedEmployeeTrips,
                delayMinutesSum,
                averageDelay,
                signal != null,
                watermark,
                sourceEventIds);
    }

    private DecisionDossier.Reason reason(
            DecisionCaseQueryService.CaseRow row,
            List<OperationTraceEvent> proof) {

        OperationTraceEvent snapshot =
                latestEvent(
                        proof,
                        "REASON",
                        "REASON_SNAPSHOT_COMPUTED");

        Map<String, Object> evidence =
                evidence(snapshot);

        String state;

        if (snapshot != null) {
            state = "COMPLETE";
        } else if ("REASONING".equals(
                row.status())) {

            state = "RUNNING";

        } else if (hasPassedReasoning(
                row.status())) {

            state = "MISSING_PROOF";

        } else {
            state = "PENDING";
        }

        return new DecisionDossier.Reason(
                state,

                decimal(
                        evidence.get(
                                "currentAvgDelay")),

                decimal(
                        evidence.get(
                                "baselineAvgDelay")),

                decimal(
                        evidence.get(
                                "deltaPct")),

                integer(
                        evidence.get(
                                "currentSampleSize")),

                integer(
                        evidence.get(
                                "baselineSampleSize")),

                decimal(
                        evidence.get(
                                "coveragePct")),

                string(
                        evidence.get(
                                "trustStatus")),

                string(
                        evidence.get(
                                "recommendation")),

                string(
                        evidence.get(
                                "methodologyVersion")),

                string(
                        evidence.get(
                                "dataCutoff")),

                bool(
                        evidence.get(
                                "futureDataExcluded")));
    }

    private DecisionDossier.Act act(
            DecisionCaseQueryService.CaseRow row,
            List<OperationTraceEvent> proof) {

        OperationTraceEvent latest =
                latestStage(
                        proof,
                        "ACT");

        String state =
                actState(
                        row.status(),
                        latest);

        return new DecisionDossier.Act(
                state,
                latest == null
                        ? null
                        : latest.eventType(),
                evidence(latest));
    }

    private DecisionDossier.Verify verify(
            DecisionCaseQueryService.CaseRow row,
            List<OperationTraceEvent> proof) {

        OperationTraceEvent latest =
                latestStage(
                        proof,
                        "VERIFY");

        String state =
                verifyState(
                        row.status(),
                        latest);

        return new DecisionDossier.Verify(
                state,
                latest == null
                        ? null
                        : latest.eventType(),
                evidence(latest));
    }

    private String actState(
            String caseStatus,
            OperationTraceEvent latest) {

        if ("MONITORING".equals(caseStatus)) {
            return "NO_ACTION_REQUIRED";
        }

        return switch (caseStatus) {
            case "ACTION_PLANNED" ->
                    "PLANNED";

            case "AWAITING_APPROVAL" ->
                    "AWAITING_APPROVAL";

            case "EXECUTING" ->
                    "EXECUTING";

            case "VERIFYING",
                 "RESOLVED" ->
                    latest == null
                            ? "MISSING_PROOF"
                            : "COMPLETE";

            default ->
                    latest == null
                            ? "PENDING"
                            : "ACTIVE";
        };
    }

    private String verifyState(
            String caseStatus,
            OperationTraceEvent latest) {

        if ("MONITORING".equals(caseStatus)) {
            return "NOT_APPLICABLE";
        }

        return switch (caseStatus) {
            case "VERIFYING" ->
                    latest == null
                            ? "MISSING_PROOF"
                            : "OBSERVING";

            case "RESOLVED" ->
                    latest == null
                            ? "MISSING_PROOF"
                            : "COMPLETE";

            default ->
                    latest == null
                            ? "PENDING"
                            : "OBSERVING";
        };
    }

    private boolean hasPassedReasoning(
            String status) {

        return Set.of(
                        "MONITORING",
                        "ACTION_PLANNED",
                        "AWAITING_APPROVAL",
                        "EXECUTING",
                        "VERIFYING",
                        "RESOLVED")
                .contains(status);
    }

    private OperationTraceEvent thresholdCrossingWindow(
            List<OperationTraceEvent> proof) {

        for (OperationTraceEvent trace : proof) {

            if (!"SENSE".equals(
                    trace.stage())) {
                continue;
            }

            if (!"WINDOW_UPDATED".equals(
                    trace.eventType())) {
                continue;
            }

            if (Boolean.TRUE.equals(
                    bool(
                            trace.evidence()
                                    .get(
                                            "thresholdCrossed")))) {

                return trace;
            }
        }

        return null;
    }

    private OperationTraceEvent latestEvent(
            List<OperationTraceEvent> proof,
            String stage,
            String eventType) {

        for (int i = proof.size() - 1;
             i >= 0;
             i--) {

            OperationTraceEvent trace =
                    proof.get(i);

            if (stage.equals(
                    trace.stage())
                    && eventType.equals(
                    trace.eventType())) {

                return trace;
            }
        }

        return null;
    }

    private OperationTraceEvent latestStage(
            List<OperationTraceEvent> proof,
            String stage) {

        for (int i = proof.size() - 1;
             i >= 0;
             i--) {

            OperationTraceEvent trace =
                    proof.get(i);

            if (stage.equals(
                    trace.stage())) {

                return trace;
            }
        }

        return null;
    }

    private List<String> sourceEventIds(
            List<OperationTraceEvent> proof) {

        LinkedHashSet<String> ids =
                new LinkedHashSet<>();

        for (OperationTraceEvent trace : proof) {

            if (!"SENSE".equals(
                    trace.stage())) {
                continue;
            }

            if (!"EVENT_ACCEPTED".equals(
                    trace.eventType())) {
                continue;
            }

            if (trace.sourceEventId() != null
                    && !trace.sourceEventId()
                    .isBlank()) {

                ids.add(
                        trace.sourceEventId());
            }
        }

        return new ArrayList<>(ids);
    }

    private Map<String, Object> evidence(
            OperationTraceEvent trace) {

        if (trace == null
                || trace.evidence() == null) {

            return Map.of();
        }

        return trace.evidence();
    }

    private Integer integer(
            Object value) {

        if (value instanceof Number number) {
            return number.intValue();
        }

        if (value == null) {
            return null;
        }

        try {
            return Integer.valueOf(
                    String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Long longValue(
            Object value) {

        if (value instanceof Number number) {
            return number.longValue();
        }

        if (value == null) {
            return null;
        }

        try {
            return Long.valueOf(
                    String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Double decimal(
            Object value) {

        if (value instanceof Number number) {
            return number.doubleValue();
        }

        if (value == null) {
            return null;
        }

        try {
            return Double.valueOf(
                    String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String string(
            Object value) {

        return value == null
                ? null
                : String.valueOf(value);
    }

    private Boolean bool(
            Object value) {

        if (value instanceof Boolean bool) {
            return bool;
        }

        if (value == null) {
            return null;
        }

        return Boolean.valueOf(
                String.valueOf(value));
    }
}