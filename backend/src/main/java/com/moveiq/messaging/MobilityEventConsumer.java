package com.moveiq.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moveiq.api.dto.MobilityEvent;
import com.moveiq.service.RedisIdempotencyService;
import com.moveiq.service.SituationService;
import com.moveiq.service.SlidingWindowSignalDetector;
import java.time.Duration;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class MobilityEventConsumer {
    private final ObjectMapper mapper;
    private final RedisIdempotencyService idempotency;
    private final SlidingWindowSignalDetector detector;
    private final SituationService situations;

    public MobilityEventConsumer(ObjectMapper mapper, RedisIdempotencyService idempotency, SlidingWindowSignalDetector detector, SituationService situations) {
        this.mapper = mapper; this.idempotency = idempotency; this.detector = detector; this.situations = situations;
    }

    @KafkaListener(topics = "${moveiq.kafka.mobility-topic}", groupId = "${spring.kafka.consumer.group-id}")
    public void onEvent(String payload) throws Exception {
        MobilityEvent event = mapper.readValue(payload, MobilityEvent.class);
        if (!idempotency.firstProcessing("event", event.businessUnit() + ":" + event.eventId(), Duration.ofDays(7))) return;
        detector.detect(event).ifPresent(situations::applySignal);
    }
}
