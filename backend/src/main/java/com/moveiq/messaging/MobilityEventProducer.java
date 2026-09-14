package com.moveiq.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moveiq.api.dto.MobilityEvent;
import java.util.concurrent.CompletableFuture;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

@Service
public class MobilityEventProducer {
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;
    private final String topic;

    public MobilityEventProducer(
            KafkaTemplate<String, String> kafka,
            ObjectMapper mapper,
            @Value("${moveiq.kafka.mobility-topic}") String topic) {
        this.kafka = kafka;
        this.mapper = mapper;
        this.topic = topic;
    }

    public void publish(MobilityEvent event) {
        publishAsync(event);
    }

    public CompletableFuture<SendResult<String, String>> publishAsync(MobilityEvent event) {
        try {
            String payload = mapper.writeValueAsString(event);
            String key = event.businessUnit() + ":" + event.eventId();
            return kafka.send(topic, key, payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid event", e);
        }
    }
}
