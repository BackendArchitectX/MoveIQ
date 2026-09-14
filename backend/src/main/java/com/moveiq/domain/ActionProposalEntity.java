package com.moveiq.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "action_proposal", schema = "moveiq")
public class ActionProposalEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "situation_id", nullable = false) private UUID situationId;
    @Column(name = "action_type", nullable = false) private String actionType;
    @Column(nullable = false) private String status = "PROPOSED";
    @Column(name = "evidence_hash", nullable = false) private String evidenceHash;
    @Column(name = "approved_by") private String approvedBy;
    @Column(name = "approved_at") private Instant approvedAt;
    @Version private long version;

    protected ActionProposalEntity() {}

    public ActionProposalEntity(UUID situationId, String actionType, String evidenceHash) {
        this.situationId = situationId;
        this.actionType = actionType;
        this.evidenceHash = evidenceHash;
    }

    public UUID getId() { return id; }
    public UUID getSituationId() { return situationId; }
    public String getActionType() { return actionType; }
    public String getStatus() { return status; }
    public String getEvidenceHash() { return evidenceHash; }

    public void approve(String approver) {
        if (!"PROPOSED".equals(status)) throw new IllegalStateException("Proposal is not pending approval");
        status = "APPROVED";
        approvedBy = approver;
        approvedAt = Instant.now();
    }
}
