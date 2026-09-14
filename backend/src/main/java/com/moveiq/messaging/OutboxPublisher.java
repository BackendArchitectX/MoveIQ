package com.moveiq.messaging;

import com.moveiq.repository.OutboxEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
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
    private final Counter published;
    private final Counter failed;

    public OutboxPublisher(
            OutboxEventRepository repository,
            KafkaTemplate<String, String> kafka,
            MeterRegistry registry,
            @Value("${moveiq.kafka.situation-topic}") String topic) {
        this.repository = repository;
        this.kafka = kafka;
        this.topic = topic;
        this.published = registry.counter("moveiq.outbox.published");
        this.failed = registry.counter("moveiq.outbox.failed");
    }

    @Scheduled(fixedDelayString = "${moveiq.outbox.publish-delay-ms:2000}")
    @Transactional
    public void publishPending() {
        repository.lockNextBatch().forEach(event -> {
            try {
                kafka.send(topic, event.getId().toString(), event.getPayload()).get(5, TimeUnit.SECONDS);
                event.markPublished();
                published.increment();
            } catch (Exception ex) {
                failed.increment();
                log.warn("outbox_publish_failed eventId={} eventType={}", event.getId(), event.getEventType(), ex);
            }
        });
    }
}
