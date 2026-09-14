package com.moveiq.service;

import static org.mockito.Mockito.*;

import com.moveiq.api.dto.MobilityEvent;
import com.moveiq.store.ProcessedEventStore;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class MobilityEventProcessingServiceTest {

    @Test
    void duplicateEventStopsBeforeDetector() {
        ProcessedEventStore store = mock(ProcessedEventStore.class);
        SlidingWindowSignalDetector detector = mock(SlidingWindowSignalDetector.class);
        SituationService situations = mock(SituationService.class);
        ReasoningService reasoning = mock(ReasoningService.class);
        OperationTraceService traces = mock(OperationTraceService.class);

        MobilityEvent event = new MobilityEvent(
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
                Instant.parse("2026-07-15T02:00:00Z"));

        when(store.claim("group", "vanta-Aus", "evt-1")).thenReturn(false);

        var service = new MobilityEventProcessingService(
                store,
                detector,
                situations,
                reasoning,
                traces,
                new SimpleMeterRegistry(),
                "group");

        service.process(event);

        verifyNoInteractions(detector, situations, reasoning, traces);
    }
}
