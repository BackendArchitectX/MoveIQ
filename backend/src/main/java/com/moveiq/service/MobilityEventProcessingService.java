package com.moveiq.service;

import com.moveiq.api.dto.DetectedSignal;
import com.moveiq.api.dto.MobilityEvent;
import com.moveiq.store.ProcessedEventStore;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MobilityEventProcessingService {

    private final ProcessedEventStore processedEvents;
    private final SlidingWindowSignalDetector detector;
    private final SituationService situations;
    private final ReasoningService reasoning;
    private final DecisionCaseService decisionCases;
    private final OperationTraceService traces;

    private final String consumerGroup;

    private final Counter processedCounter;
    private final Counter duplicateCounter;
    private final Counter signalCounter;

    public MobilityEventProcessingService(
            ProcessedEventStore processedEvents,
            SlidingWindowSignalDetector detector,
            SituationService situations,
            ReasoningService reasoning,
            DecisionCaseService decisionCases,
            OperationTraceService traces,
            MeterRegistry meterRegistry,
            @Value("${spring.kafka.consumer.group-id}")
            String consumerGroup) {

        this.processedEvents =
                processedEvents;

        this.detector =
                detector;

        this.situations =
                situations;

        this.reasoning =
                reasoning;

        this.decisionCases =
                decisionCases;

        this.traces =
                traces;

        this.consumerGroup =
                consumerGroup;

        this.processedCounter =
                meterRegistry.counter(
                        "moveiq.events.processed");

        this.duplicateCounter =
                meterRegistry.counter(
                        "moveiq.events.duplicate");

        this.signalCounter =
                meterRegistry.counter(
                        "moveiq.signals.detected");
    }

    @Transactional
    public void process(
            MobilityEvent event) {

        /*
         * PostgreSQL remains the durable idempotency boundary.
         *
         * Replay events are scoped by replaySessionId so a new
         * historical replay is allowed to process the same source
         * event again.
         */
        if (!processedEvents.claim(
                consumerGroup,
                event.replaySessionId(),
                event.businessUnit(),
                event.eventId())) {

            duplicateCounter.increment();
            return;
        }

        processedCounter.increment();

        String scope =
                scopeKey(event);

        /*
         * Redis performs the distributed event-time detector
         * transition for qualifying observations.
         *
         * Routine completions/minor replay delays can return a
         * non-included evaluation without touching Redis.
         */
        DetectionEvaluation evaluation =
                detector.evaluate(
                        event);

        UUID decisionId =
                evaluation.decisionId();

        /*
         * Only events belonging to an active detection episode
         * create/update a durable decision case.
         */
        if (decisionId != null) {

            decisionCases.ensureSensing(
                    decisionId,
                    event.replaySessionId(),
                    scope,
                    event.occurredAt());
        }

        /*
         * All source events remain visible in the proof ledger,
         * including events that do not qualify as disruption evidence.
         */
        traces.record(
                event.replaySessionId(),
                decisionId,
                null,
                event.eventId(),
                scope,
                "SENSE",
                "EVENT_ACCEPTED",
                event.occurredAt(),
                "Mobility event accepted for detection evaluation",
                Map.of(
                        "eventType",
                        event.eventType(),
                        "affectedEmployees",
                        event.affectedEmployees(),
                        "delayMinutes",
                        event.delayMinutes()));

        Map<String, Object> windowEvidence =
                new LinkedHashMap<>();

        windowEvidence.put(
                "windowEventCount",
                evaluation.windowEventCount());

        windowEvidence.put(
                "threshold",
                evaluation.threshold());

        windowEvidence.put(
                "affectedEmployeeTrips",
                evaluation.affectedEmployees());

        windowEvidence.put(
                "delayMinutesSum",
                evaluation.delayMinutes());

        if (evaluation.windowEventCount()
                > 0) {

            windowEvidence.put(
                    "averageDelayMinutes",
                    evaluation.delayMinutes()
                            / (double)
                            evaluation.windowEventCount());
        }

        windowEvidence.put(
                "thresholdCrossed",
                evaluation.thresholdCrossed());

        windowEvidence.put(
                "includedInWindow",
                evaluation.includedInWindow());

        windowEvidence.put(
                "watermarkEpochMillis",
                evaluation.watermarkEpochMillis());

        /*
         * Do not claim WINDOW_UPDATED when Redis did not actually
         * insert the event.
         *
         * This covers:
         * - non-qualifying TRIP_COMPLETED events
         * - minor LATE_ARRIVAL events
         * - events outside the active event-time window
         * - Redis-idempotent retry paths
         */
        String detectionEventType;

        String detectionSummary;

        if (evaluation.includedInWindow()) {

            detectionEventType =
                    "WINDOW_UPDATED";

            detectionSummary =
                    evaluation.thresholdCrossed()
                            ? "Detection window reached its threshold"
                            : "Detection window updated";

        } else if (evaluation.thresholdCrossed()) {

            detectionEventType =
                    "WINDOW_REPLAYED";

            detectionSummary =
                    "Threshold-owner event replayed without duplicating window evidence";

        } else {

            detectionEventType =
                    "WINDOW_NOT_UPDATED";

            detectionSummary =
                    "Event observed but did not modify the active disruption window";
        }

        traces.record(
                event.replaySessionId(),
                decisionId,
                null,
                event.eventId(),
                scope,
                "SENSE",
                detectionEventType,
                event.occurredAt(),
                detectionSummary,
                windowEvidence);

        /*
         * Most observations stop here.
         */
        if (evaluation.signal()
                .isEmpty()) {

            reevaluateMonitoringCase(
                    event,
                    evaluation,
                    decisionId,
                    scope);

            return;
        }

        if (decisionId == null) {

            throw new IllegalStateException(
                    "Detected signal has no decisionId");
        }

        var signal =
                evaluation.signal()
                        .orElseThrow();

        signalCounter.increment();

        decisionCases.markDetected(
                decisionId,
                event.eventId(),
                signal.detectedAt());

        traces.record(
                event.replaySessionId(),
                decisionId,
                null,
                event.eventId(),
                scope,
                "SENSE",
                "SIGNAL_DETECTED",
                signal.detectedAt(),
                "Distributed detection threshold crossed",
                Map.of(
                        "signalType",
                        signal.signalType(),
                        "windowEventCount",
                        signal.windowEventCount(),
                        "affectedEmployeeTrips",
                        signal.affectedEmployees(),
                        "delayMinutesSum",
                        signal.delayMinutes()));

        /*
         * Correlate the signal to the durable business situation.
         */
        var situation =
                situations.applySignal(
                        signal);

        decisionCases
                .attachSituationAndStartReasoning(
                        decisionId,
                        situation.getId());

        Map<String, Object> correlationEvidence =
                new LinkedHashMap<>();

        correlationEvidence.put(
                "situationId",
                situation.getId()
                        .toString());

        correlationEvidence.put(
                "situationType",
                situation.getSituationType());

        correlationEvidence.put(
                "affectedEmployees",
                situation.getAffectedEmployees());

        correlationEvidence.put(
                "delayMinutes",
                situation.getDelayMinutes());

        traces.record(
                event.replaySessionId(),
                decisionId,
                situation.getId(),
                event.eventId(),
                scope,
                "REASON",
                "SITUATION_CORRELATED",
                signal.detectedAt(),
                "Detected signal correlated to durable operational situation",
                correlationEvidence);

        /*
         * Deterministic reasoning.
         *
         * ReasoningService applies the replay-time cutoff so future
         * historical rows cannot leak into this decision.
         */
        var reason =
                reasoning.computeAndStore(
                        situation,
                        signal);

        decisionCases.markReasoned(
                decisionId,
                reason.recommendation());

        Map<String, Object> reasonEvidence =
                new LinkedHashMap<>();

        reasonEvidence.put(
                "situationType",
                situation.getSituationType());

        reasonEvidence.put(
                "affectedEmployees",
                situation.getAffectedEmployees());

        reasonEvidence.put(
                "delayMinutes",
                situation.getDelayMinutes());

        reasonEvidence.put(
                "currentAvgDelay",
                reason.currentAvgDelay());

        if (reason.baselineAvgDelay()
                != null) {

            reasonEvidence.put(
                    "baselineAvgDelay",
                    reason.baselineAvgDelay());
        }

        if (reason.deltaPct()
                != null) {

            reasonEvidence.put(
                    "deltaPct",
                    reason.deltaPct());
        }

        reasonEvidence.put(
                "currentSampleSize",
                reason.currentSampleSize());

        reasonEvidence.put(
                "baselineSampleSize",
                reason.baselineSampleSize());

        reasonEvidence.put(
                "coveragePct",
                reason.coveragePct());

        reasonEvidence.put(
                "trustStatus",
                reason.trustStatus());

        reasonEvidence.put(
                "recommendation",
                reason.recommendation());

        reasonEvidence.put(
                "methodologyVersion",
                reason.methodologyVersion());

        reasonEvidence.put(
                "dataCutoff",
                signal.detectedAt()
                        .toString());

        reasonEvidence.put(
                "futureDataExcluded",
                true);

        traces.record(
                event.replaySessionId(),
                decisionId,
                situation.getId(),
                event.eventId(),
                scope,
                "REASON",
                "REASON_SNAPSHOT_COMPUTED",
                signal.detectedAt(),
                "Deterministic context and baseline computed without future replay data",
                reasonEvidence);
    }
    /**
     * Re-evaluate an existing MONITORING case when genuinely new,
     * qualifying evidence arrives in the same active disruption episode.
     *
     * <p>This does not emit another SIGNAL_DETECTED and does not add
     * another Situation contribution.
     */
    private void reevaluateMonitoringCase(
            MobilityEvent event,
            DetectionEvaluation evaluation,
            UUID decisionId,
            String scope) {

        if (decisionId == null
                || !evaluation.includedInWindow()
                || evaluation.windowEventCount()
                < evaluation.threshold()) {

            return;
        }

        var claimedSituationId =
                decisionCases
                        .claimMonitoringForReevaluation(
                                decisionId);

        if (claimedSituationId.isEmpty()) {
            return;
        }

        UUID situationId =
                claimedSituationId.orElseThrow();

        var situation =
                situations.requireById(
                        situationId);

        DetectedSignal reevaluationSignal =
                new DetectedSignal(
                        event.eventId(),
                        event.businessUnit(),
                        "MOBILITY_DISRUPTION",
                        event.office(),
                        event.shift(),
                        event.direction(),
                        evaluation.affectedEmployees(),
                        evaluation.delayMinutes(),
                        evaluation.windowEventCount(),
                        event.occurredAt());

        traces.record(
                event.replaySessionId(),
                decisionId,
                situationId,
                event.eventId(),
                scope,
                "REASON",
                "MONITOR_REEVALUATION_TRIGGERED",
                event.occurredAt(),
                "New qualifying evidence triggered re-evaluation of the monitoring decision",
                Map.of(
                        "windowEventCount",
                        evaluation.windowEventCount(),
                        "threshold",
                        evaluation.threshold(),
                        "affectedEmployeeTrips",
                        evaluation.affectedEmployees(),
                        "delayMinutesSum",
                        evaluation.delayMinutes()));

        var reason =
                reasoning.computeAndStore(
                        situation,
                        reevaluationSignal);

        decisionCases.markReasoned(
                decisionId,
                reason.recommendation());

        Map<String, Object> reasonEvidence =
                new LinkedHashMap<>();

        reasonEvidence.put(
                "reasonCycle",
                "MONITOR_REEVALUATION");

        reasonEvidence.put(
                "situationType",
                situation.getSituationType());

        reasonEvidence.put(
                "affectedEmployees",
                evaluation.affectedEmployees());

        reasonEvidence.put(
                "delayMinutes",
                evaluation.delayMinutes());

        reasonEvidence.put(
                "windowEventCount",
                evaluation.windowEventCount());

        reasonEvidence.put(
                "currentAvgDelay",
                reason.currentAvgDelay());

        if (reason.baselineAvgDelay() != null) {

            reasonEvidence.put(
                    "baselineAvgDelay",
                    reason.baselineAvgDelay());
        }

        if (reason.deltaPct() != null) {

            reasonEvidence.put(
                    "deltaPct",
                    reason.deltaPct());
        }

        reasonEvidence.put(
                "currentSampleSize",
                reason.currentSampleSize());

        reasonEvidence.put(
                "baselineSampleSize",
                reason.baselineSampleSize());

        reasonEvidence.put(
                "coveragePct",
                reason.coveragePct());

        reasonEvidence.put(
                "trustStatus",
                reason.trustStatus());

        reasonEvidence.put(
                "recommendation",
                reason.recommendation());

        reasonEvidence.put(
                "methodologyVersion",
                reason.methodologyVersion());

        reasonEvidence.put(
                "dataCutoff",
                event.occurredAt()
                        .toString());

        reasonEvidence.put(
                "futureDataExcluded",
                true);

        traces.record(
                event.replaySessionId(),
                decisionId,
                situationId,
                event.eventId(),
                scope,
                "REASON",
                "REASON_SNAPSHOT_COMPUTED",
                event.occurredAt(),
                "Monitoring decision re-evaluated against newly arrived qualifying evidence",
                reasonEvidence);
    }

    private String scopeKey(
            MobilityEvent event) {

        return String.join(
                "|",
                safe(
                        event.businessUnit()),
                safe(
                        event.office()),
                safe(
                        event.shift()),
                safe(
                        event.direction()),
                safe(
                        event.eventType()));
    }

    private String safe(
            String value) {

        return value == null
                ? "_"
                : value;
    }
}