package com.moveiq.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "situation", schema = "moveiq", uniqueConstraints = @UniqueConstraint(name = "uk_situation_correlation", columnNames = "correlation_key"))
public class SituationEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "correlation_key", nullable = false, updatable = false) private String correlationKey;
    @Column(name = "business_unit", nullable = false) private String businessUnit;
    @Column(name = "situation_type", nullable = false) private String situationType;
    private String office;
    private String shift;
    private String direction;
    @Column(nullable = false) private String status = "DETECTED";
    @Column(nullable = false) private String priority = "MEDIUM";
    @Column(name = "affected_employees", nullable = false) private long affectedEmployees;
    @Column(name = "delay_minutes", nullable = false) private long delayMinutes;
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false) private Instant updatedAt = Instant.now();
    @Version private long version;

    protected SituationEntity() {}

    public SituationEntity(String correlationKey, String businessUnit, String situationType, String office, String shift, String direction) {
        this.correlationKey = correlationKey;
        this.businessUnit = businessUnit;
        this.situationType = situationType;
        this.office = office;
        this.shift = shift;
        this.direction = direction;
    }

    public UUID getId() { return id; }
    public String getCorrelationKey() { return correlationKey; }
    public String getBusinessUnit() { return businessUnit; }
    public String getSituationType() { return situationType; }
    public String getStatus() { return status; }
    public long getAffectedEmployees() { return affectedEmployees; }
    public long getDelayMinutes() { return delayMinutes; }
    public void addImpact(long employees, long delay) {
        affectedEmployees += employees;
        delayMinutes += delay;
        updatedAt = Instant.now();
    }
}
