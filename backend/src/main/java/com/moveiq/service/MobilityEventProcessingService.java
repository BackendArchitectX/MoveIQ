package com.moveiq.service;

import com.moveiq.api.dto.MobilityEvent;
import com.moveiq.store.ProcessedEventStore;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MobilityEventProcessingService {
    private final ProcessedEventStore processedEvents;
    private final SlidingWindowSignalDetector detector;
    private final SituationService situations;
    private final String consumerGroup;
    private final Counter processedCounter;
    private final Counter duplicateCounter;
    private final Counter signalCounter;

    public MobilityEventProcessingService(
            ProcessedEventStore processedEvents,
            SlidingWindowSignalDetector detector,
            SituationService situations,
            MeterRegistry meterRegistry,
            @Value("${spring.kafka.consumer.group-id}") String consumerGroup) {
        this.processedEvents = processedEvents;
        this.detector = detector;
        this.situations = situations;
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
        detector.detect(event).ifPresent(signal -> {
            signalCounter.increment();
            situations.applySignal(signal);
        });
    }
}
