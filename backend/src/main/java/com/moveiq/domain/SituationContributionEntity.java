package com.moveiq.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "situation_contribution", schema = "moveiq", uniqueConstraints = @UniqueConstraint(name = "uk_situation_source", columnNames = {"situation_id", "source_event_id"}))
public class SituationContributionEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "situation_id", nullable = false) private UUID situationId;
    @Column(name = "source_event_id", nullable = false) private String sourceEventId;
    @Column(name = "affected_employees", nullable = false) private long affectedEmployees;
    @Column(name = "delay_minutes", nullable = false) private long delayMinutes;
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();

    protected SituationContributionEntity() {}
    public SituationContributionEntity(UUID situationId, String sourceEventId, long affectedEmployees, long delayMinutes) {
        this.situationId = situationId;
        this.sourceEventId = sourceEventId;
        this.affectedEmployees = affectedEmployees;
        this.delayMinutes = delayMinutes;
    }
}
