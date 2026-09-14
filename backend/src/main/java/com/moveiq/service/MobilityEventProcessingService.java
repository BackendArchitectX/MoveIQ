package com.moveiq.service;

import com.moveiq.api.dto.MobilityEvent;
import com.moveiq.store.ProcessedEventStore;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MobilityEventProcessingService {

    private final ProcessedEventStore processedEvents;
    private final SlidingWindowSignalDetector detector;
    private final SituationService situations;
    private final ReasoningService reasoning;
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
            OperationTraceService traces,
            MeterRegistry meterRegistry,
            @Value("${spring.kafka.consumer.group-id}") String consumerGroup) {

        this.processedEvents = processedEvents;
        this.detector = detector;
        this.situations = situations;
        this.reasoning = reasoning;
        this.traces = traces;
        this.consumerGroup = consumerGroup;

        this.processedCounter = meterRegistry.counter("moveiq.events.processed");
        this.duplicateCounter = meterRegistry.counter("moveiq.events.duplicate");
        this.signalCounter = meterRegistry.counter("moveiq.signals.detected");
    }

    @Transactional
    public void process(MobilityEvent event) {
        if (!processedEvents.claim(consumerGroup, event.businessUnit(), event.eventId())) {
            duplicateCounter.increment();
            return;
        }

        processedCounter.increment();

        traces.record(
                null, null, event.eventId(), scopeKey(event), "SENSE", "EVENT_ACCEPTED", event.occurredAt(),
                "Mobility event accepted for distributed detection",
                Map.of(
                        "eventType", event.eventType(),
                        "affectedEmployees", event.affectedEmployees(),
                        "delayMinutes", event.delayMinutes()));

        DetectionEvaluation evaluation = detector.evaluate(event);

        traces.record(
                null, null, event.eventId(), scopeKey(event), "SENSE", "WINDOW_UPDATED", event.occurredAt(),
                evaluation.thresholdCrossed()
                        ? "Detection window reached its threshold"
                        : "Detection window updated",
                Map.of(
                        "windowEventCount", evaluation.windowEventCount(),
                        "threshold", evaluation.threshold(),
                        "affectedEmployees", evaluation.affectedEmployees(),
                        "delayMinutes", evaluation.delayMinutes(),
                        "thresholdCrossed", evaluation.thresholdCrossed(),
                        "includedInWindow", evaluation.includedInWindow(),
                        "watermarkEpochMillis", evaluation.watermarkEpochMillis()));

        if (evaluation.signal().isEmpty()) return;

        var signal = evaluation.signal().orElseThrow();
        signalCounter.increment();

        traces.record(
                null, null, event.eventId(), scopeKey(event), "SENSE", "SIGNAL_DETECTED", signal.detectedAt(),
                "Distributed detection threshold crossed",
                Map.of(
                        "signalType", signal.signalType(),
                        "windowEventCount", signal.windowEventCount(),
                        "affectedEmployees", signal.affectedEmployees(),
                        "delayMinutes", signal.delayMinutes()));

        var situation = situations.applySignal(signal);
        var reason = reasoning.computeAndStore(situation, signal);

        Map<String, Object> reasonEvidence = new LinkedHashMap<>();
        reasonEvidence.put("situationType", situation.getSituationType());
        reasonEvidence.put("affectedEmployees", situation.getAffectedEmployees());
        reasonEvidence.put("delayMinutes", situation.getDelayMinutes());
        reasonEvidence.put("currentAvgDelay", reason.currentAvgDelay());
        reasonEvidence.put("baselineAvgDelay", reason.baselineAvgDelay());
        reasonEvidence.put("deltaPct", reason.deltaPct());
        reasonEvidence.put("currentSampleSize", reason.currentSampleSize());
        reasonEvidence.put("baselineSampleSize", reason.baselineSampleSize());
        reasonEvidence.put("coveragePct", reason.coveragePct());
        reasonEvidence.put("trustStatus", reason.trustStatus());
        reasonEvidence.put("recommendation", reason.recommendation());
        reasonEvidence.put("methodologyVersion", reason.methodologyVersion());

        traces.record(
                null,
                situation.getId(),
                event.eventId(),
                scopeKey(event),
                "REASON",
                "REASON_SNAPSHOT_COMPUTED",
                signal.detectedAt(),
                "Deterministic context and baseline computed without future replay data",
                reasonEvidence);
    }

    private String scopeKey(MobilityEvent event) {
        return String.join(
                "|",
                safe(event.businessUnit()),
                safe(event.office()),
                safe(event.shift()),
                safe(event.direction()),
                safe(event.eventType()));
    }

    private String safe(String value) {
        return value == null ? "_" : value;
    }
}
