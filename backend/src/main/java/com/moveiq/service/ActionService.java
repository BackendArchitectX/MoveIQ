package com.moveiq.service;

import com.moveiq.api.dto.ActionDtos.ApproveActionRequest;
import com.moveiq.api.dto.ActionDtos.ExecutionReceipt;
import com.moveiq.api.dto.ActionDtos.ProposeActionRequest;
import com.moveiq.domain.ActionExecutionEntity;
import com.moveiq.domain.ActionProposalEntity;
import com.moveiq.repository.ActionExecutionRepository;
import com.moveiq.repository.ActionProposalRepository;
import com.moveiq.repository.SituationRepository;
import java.time.Duration;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ActionService {
    private final ActionProposalRepository proposals;
    private final ActionExecutionRepository executions;
    private final SituationRepository situations;
    private final RedisIdempotencyService idempotency;

    public ActionService(ActionProposalRepository proposals, ActionExecutionRepository executions, SituationRepository situations, RedisIdempotencyService idempotency) {
        this.proposals = proposals; this.executions = executions; this.situations = situations; this.idempotency = idempotency;
    }

    @Transactional
    public ActionProposalEntity propose(UUID situationId, ProposeActionRequest request) {
        if (!situations.existsById(situationId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Situation not found");
        return proposals.save(new ActionProposalEntity(situationId, request.actionType(), request.evidenceHash()));
    }

    @Transactional
    public ExecutionReceipt approveAndExecute(UUID proposalId, ApproveActionRequest request) {
        ActionProposalEntity proposal = proposals.findById(proposalId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Action proposal not found"));

        if (!proposal.getEvidenceHash().equals(request.currentEvidenceHash())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Evidence changed; recommendation is stale and must be revalidated");
        }

        var existing = executions.findByIdempotencyKey(request.idempotencyKey());
        if (existing.isPresent()) return receipt(proposalId, existing.get());

        if (!idempotency.firstProcessing("action", request.idempotencyKey(), Duration.ofHours(24))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Action is already being executed");
        }

        proposal.approve(request.approvedBy());
        proposals.save(proposal);

        String externalReference = "SIM-" + proposalId;
        ActionExecutionEntity execution = executions.save(new ActionExecutionEntity(proposalId, request.idempotencyKey(), "EXECUTED", externalReference));
        return receipt(proposalId, execution);
    }

    private ExecutionReceipt receipt(UUID proposalId, ActionExecutionEntity execution) {
        return new ExecutionReceipt(execution.getId(), proposalId, execution.getStatus(), execution.getExternalReference());
    }
}
