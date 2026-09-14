package com.moveiq.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moveiq.api.dto.MobilityEvent;
import com.moveiq.service.MobilityEventProcessingService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class MobilityEventConsumer {
    private final ObjectMapper mapper;
    private final MobilityEventProcessingService processing;

    public MobilityEventConsumer(ObjectMapper mapper, MobilityEventProcessingService processing) {
        this.mapper = mapper;
        this.processing = processing;
    }

    @KafkaListener(
            topics = "${moveiq.kafka.mobility-topic}",
            groupId = "${spring.kafka.consumer.group-id}")
    public void onEvent(String payload) throws Exception {
        MobilityEvent event = mapper.readValue(payload, MobilityEvent.class);
        processing.process(event);
    }
}
