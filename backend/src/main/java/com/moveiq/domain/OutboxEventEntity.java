package com.moveiq.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_event", schema = "moveiq")
public class OutboxEventEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "aggregate_type", nullable = false) private String aggregateType;
    @Column(name = "aggregate_id", nullable = false) private String aggregateId;
    @Column(name = "event_type", nullable = false) private String eventType;
    @Column(nullable = false, columnDefinition = "text") private String payload;
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();
    @Column(name = "published_at") private Instant publishedAt;

    protected OutboxEventEntity() {}
    public OutboxEventEntity(String aggregateType, String aggregateId, String eventType, String payload) {
        this.aggregateType = aggregateType; this.aggregateId = aggregateId; this.eventType = eventType; this.payload = payload;
    }
    public UUID getId() { return id; }
    public String getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public void markPublished() { publishedAt = Instant.now(); }
}
