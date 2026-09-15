package com.moveiq.service;

import com.moveiq.api.dto.ActionDtos.ApproveActionRequest;
import com.moveiq.api.dto.ActionDtos.ExecutionReceipt;
import com.moveiq.api.dto.ActionDtos.ProposeActionRequest;
import com.moveiq.domain.ActionExecutionEntity;
import com.moveiq.domain.ActionProposalEntity;
import com.moveiq.integration.ActionExecutionRouter;
import com.moveiq.repository.ActionExecutionRepository;
import com.moveiq.repository.ActionProposalRepository;
import com.moveiq.repository.SituationRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.Map;
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
    private final EvidenceHashService evidenceHash;
    private final ActionExecutionRouter executionRouter;
    private final OperationTraceService traces;
    private final Counter staleCounter;

    public ActionService(
            ActionProposalRepository proposals,
            ActionExecutionRepository executions,
            SituationRepository situations,
            EvidenceHashService evidenceHash,
            ActionExecutionRouter executionRouter,
            OperationTraceService traces,
            MeterRegistry meterRegistry) {
        this.proposals = proposals;
        this.executions = executions;
        this.situations = situations;
        this.evidenceHash = evidenceHash;
        this.executionRouter = executionRouter;
        this.traces = traces;
        this.staleCounter = meterRegistry.counter("moveiq.actions.stale");
    }

    @Transactional
    public ActionProposalEntity propose(UUID situationId, ProposeActionRequest request) {
        if (!situations.existsById(situationId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Situation not found");
        }
        executionRouter.assertSupported(request.actionType());
        String hash = evidenceHash.compute(situationId);
        ActionProposalEntity proposal = proposals.save(new ActionProposalEntity(situationId, request.actionType(), hash));
        proposals.flush();
        traces.record(
                null,
                situationId,
                null,
                null,
                "ACT",
                "ACTION_PROPOSED",
                Instant.now(),
                "Human approval required before action execution",
                Map.of(
                        "proposalId", proposal.getId().toString(),
                        "actionType", proposal.getActionType(),
                        "status", proposal.getStatus(),
                        "executorMode", "SIMULATED"));
        return proposal;
    }

    @Transactional
    public ExecutionReceipt approveAndExecute(UUID proposalId, ApproveActionRequest request) {
        var existing = executions.findByIdempotencyKey(request.idempotencyKey());
        if (existing.isPresent()) {
            ActionExecutionEntity execution = existing.get();
            if (!proposalId.equals(execution.getProposalId())) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Idempotency key was already used for a different action proposal");
            }
            return receipt(proposalId, execution);
        }

        ActionProposalEntity proposal = proposals.findForUpdate(proposalId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Action proposal not found"));

        if (!"PROPOSED".equals(proposal.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Action proposal is no longer pending");
        }

        String currentHash = evidenceHash.compute(proposal.getSituationId());
        if (!proposal.getEvidenceHash().equals(currentHash)) {
            staleCounter.increment();
            traces.record(
                    null,
                    proposal.getSituationId(),
                    null,
                    null,
                    "ACT",
                    "ACTION_BLOCKED_STALE_EVIDENCE",
                    Instant.now(),
                    "Approval blocked because operational evidence changed",
                    Map.of(
                            "proposalId", proposalId.toString(),
                            "actionType", proposal.getActionType(),
                            "executorMode", "SIMULATED"));
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Evidence changed; regenerate the recommendation before approval");
        }

        var result = executionRouter.execute(proposal, request.idempotencyKey());
        proposal.approve(request.approvedBy());
        ActionExecutionEntity execution = executions.save(new ActionExecutionEntity(
                proposalId, request.idempotencyKey(), result.status(), result.externalReference()));
        executions.flush();
        traces.record(
                null,
                proposal.getSituationId(),
                null,
                null,
                "ACT",
                "ACTION_EXECUTED",
                execution.getExecutedAt(),
                "Approved action executed through configured simulator",
                Map.of(
                        "proposalId", proposalId.toString(),
                        "executionId", execution.getId().toString(),
                        "actionType", proposal.getActionType(),
                        "status", execution.getStatus(),
                        "externalReference", result.externalReference(),
                        "executorMode", "SIMULATED"));
        return receipt(proposalId, execution);
    }

    private ExecutionReceipt receipt(UUID proposalId, ActionExecutionEntity execution) {
        return new ExecutionReceipt(execution.getId(), proposalId, execution.getStatus(), execution.getExternalReference());
    }
}
