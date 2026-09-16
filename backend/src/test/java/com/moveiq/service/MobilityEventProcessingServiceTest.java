package com.moveiq.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.moveiq.api.dto.MobilityEvent;
import com.moveiq.store.ProcessedEventStore;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MobilityEventProcessingServiceTest {

    @Test
    void duplicateEventStopsBeforeAnyDownstreamProcessing() {

        ProcessedEventStore store =
                mock(ProcessedEventStore.class);

        SlidingWindowSignalDetector detector =
                mock(SlidingWindowSignalDetector.class);

        SituationService situations =
                mock(SituationService.class);

        ReasoningService reasoning =
                mock(ReasoningService.class);

        DecisionCaseService decisionCases =
                mock(DecisionCaseService.class);

        OperationTraceService traces =
                mock(OperationTraceService.class);

        MobilityEvent event =
                new MobilityEvent(
                        "evt-1",
                        "vanta-Aus",
                        1L,
                        "VEHICLE_STOPPAGE",
                        "Cedar",
                        "03:00",
                        "LOGIN",
                        "vendor-a",
                        5,
                        10,
                        Instant.parse(
                                "2026-07-15T02:00:00Z"));

        when(store.claim(
                "group",
                null,
                "vanta-Aus",
                "evt-1"))
                .thenReturn(false);

        var service =
                new MobilityEventProcessingService(
                        store,
                        detector,
                        situations,
                        reasoning,
                        decisionCases,
                        traces,
                        new SimpleMeterRegistry(),
                        "group");

        service.process(event);

        verifyNoInteractions(
                detector,
                situations,
                reasoning,
                decisionCases,
                traces);
    }

    @Test
    void replaySessionIsPropagatedToDecisionCaseAndSenseTraces() {

        ProcessedEventStore store =
                mock(ProcessedEventStore.class);

        SlidingWindowSignalDetector detector =
                mock(SlidingWindowSignalDetector.class);

        SituationService situations =
                mock(SituationService.class);

        ReasoningService reasoning =
                mock(ReasoningService.class);

        DecisionCaseService decisionCases =
                mock(DecisionCaseService.class);

        OperationTraceService traces =
                mock(OperationTraceService.class);

        UUID replaySessionId =
                UUID.fromString(
                        "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

        UUID decisionId =
                UUID.fromString(
                        "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

        Instant occurredAt =
                Instant.parse(
                        "2026-07-15T02:00:00Z");

        MobilityEvent event =
                new MobilityEvent(
                        "evt-replay-1",
                        "PUNE",
                        1L,
                        "LATE_ARRIVAL",
                        "HINJEWADI",
                        "09:00",
                        "IN",
                        "Vendor-A",
                        5,
                        10,
                        occurredAt,
                        replaySessionId);

        when(store.claim(
                "group",
                replaySessionId,
                "PUNE",
                "evt-replay-1"))
                .thenReturn(true);

        when(detector.evaluate(event))
                .thenReturn(
                        new DetectionEvaluation(
                                decisionId,
                                1,
                                3,
                                5,
                                10,
                                false,
                                true,
                                occurredAt.toEpochMilli(),
                                Optional.empty()));

        var service =
                new MobilityEventProcessingService(
                        store,
                        detector,
                        situations,
                        reasoning,
                        decisionCases,
                        traces,
                        new SimpleMeterRegistry(),
                        "group");

        service.process(event);

        verify(decisionCases)
                .ensureSensing(
                        decisionId,
                        replaySessionId,
                        "PUNE|HINJEWADI|09:00|IN|LATE_ARRIVAL",
                        occurredAt);

        verify(traces, times(2))
                .record(
                        eq(replaySessionId),
                        eq(decisionId),
                        isNull(),
                        eq("evt-replay-1"),
                        eq(
                                "PUNE|HINJEWADI|09:00|IN|LATE_ARRIVAL"),
                        eq("SENSE"),
                        anyString(),
                        eq(occurredAt),
                        anyString(),
                        anyMap());

        verifyNoInteractions(
                situations,
                reasoning);
    }
}