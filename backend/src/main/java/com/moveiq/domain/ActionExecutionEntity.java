package com.moveiq.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "action_execution", schema = "moveiq", uniqueConstraints = @UniqueConstraint(name = "uk_action_idempotency", columnNames = "idempotency_key"))
public class ActionExecutionEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "proposal_id", nullable = false) private UUID proposalId;
    @Column(name = "idempotency_key", nullable = false) private String idempotencyKey;
    @Column(nullable = false) private String status;
    @Column(name = "external_reference") private String externalReference;
    @Column(name = "executed_at", nullable = false) private Instant executedAt = Instant.now();

    protected ActionExecutionEntity() {}
    public ActionExecutionEntity(UUID proposalId, String idempotencyKey, String status, String externalReference) {
        this.proposalId = proposalId; this.idempotencyKey = idempotencyKey; this.status = status; this.externalReference = externalReference;
    }
    public UUID getId() { return id; }
    public UUID getProposalId() { return proposalId; }
    public String getStatus() { return status; }
    public String getExternalReference() { return externalReference; }
    public Instant getExecutedAt() { return executedAt; }
}
