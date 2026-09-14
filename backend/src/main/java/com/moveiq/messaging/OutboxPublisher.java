package com.moveiq.messaging;

import com.moveiq.repository.OutboxEventRepository;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafka;
    private final String topic;

    public OutboxPublisher(OutboxEventRepository repository, KafkaTemplate<String, String> kafka, @Value("${moveiq.kafka.situation-topic}") String topic) {
        this.repository = repository; this.kafka = kafka; this.topic = topic;
    }

    @Scheduled(fixedDelayString = "${moveiq.outbox.publish-delay-ms:2000}")
    @Transactional
    public void publishPending() {
        repository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc().forEach(event -> {
            try {
                kafka.send(topic, event.getId().toString(), event.getPayload()).get(5, TimeUnit.SECONDS);
                event.markPublished();
                repository.save(event);
            } catch (Exception ex) {
                log.warn("Outbox publish failed for {}", event.getId(), ex);
            }
        });
    }
}
