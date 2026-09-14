package com.moveiq.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moveiq.api.dto.MobilityEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class MobilityEventProducer {
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;
    private final String topic;

    public MobilityEventProducer(KafkaTemplate<String, String> kafka, ObjectMapper mapper, @Value("${moveiq.kafka.mobility-topic}") String topic) {
        this.kafka = kafka; this.mapper = mapper; this.topic = topic;
    }

    public void publish(MobilityEvent event) {
        try { kafka.send(topic, event.businessUnit() + ":" + event.eventId(), mapper.writeValueAsString(event)); }
        catch (JsonProcessingException e) { throw new IllegalArgumentException("Invalid event", e); }
    }
}
