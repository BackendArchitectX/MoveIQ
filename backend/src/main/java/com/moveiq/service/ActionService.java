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

        this.staleCounter =
                meterRegistry.counter(
                        "moveiq.actions.stale");
    }

    /**
     * Existing manual API path.
     */
    @Transactional
    public ActionProposalEntity propose(
            UUID situationId,
            ProposeActionRequest request) {

        validateAction(
                situationId,
                request.actionType());

        return createProposal(
                situationId,
                request.actionType(),
                null);
    }

    /**
     * Autonomous policy path.
     *
     * Reuses an existing proposal for the same situation/action
     * if one already exists. This prevents a replayed policy event
     * from creating another proposal unnecessarily.
     */
    @Transactional
    public ActionProposalEntity proposeForDecision(
            UUID situationId,
            ProposeActionRequest request,
            ActionTraceContext context) {

        validateAction(
                situationId,
                request.actionType());

        var existing =
                proposals
                        .findFirstBySituationIdAndActionType(
                                situationId,
                                request.actionType());

        if (existing.isPresent()) {

            ActionProposalEntity proposal =
                    existing.get();

            record(
                    context,
                    situationId,
                    "ACTION_PROPOSAL_REUSED",
                    context.eventTime(),
                    "Existing action proposal reused by autonomous policy",
                    Map.of(
                            "proposalId",
                            proposal.getId().toString(),
                            "actionType",
                            proposal.getActionType(),
                            "status",
                            proposal.getStatus(),
                            "approvalRequired",
                            context.approvalRequired(),
                            "executorMode",
                            "SIMULATED"));

            return proposal;
        }

        return createProposal(
                situationId,
                request.actionType(),
                context);
    }

    /**
     * Existing manual approval API path.
     */
    @Transactional
    public ExecutionReceipt approveAndExecute(
            UUID proposalId,
            ApproveActionRequest request) {

        return approveAndExecuteInternal(
                proposalId,
                request,
                null);
    }

    /**
     * Autonomous low-risk execution path.
     */
    @Transactional
    public ExecutionReceipt approveAndExecuteForDecision(
            UUID proposalId,
            ApproveActionRequest request,
            ActionTraceContext context) {

        return approveAndExecuteInternal(
                proposalId,
                request,
                context);
    }

    private ActionProposalEntity createProposal(
            UUID situationId,
            String actionType,
            ActionTraceContext context) {

        String hash =
                evidenceHash.compute(
                        situationId);

        ActionProposalEntity proposal =
                proposals.save(
                        new ActionProposalEntity(
                                situationId,
                                actionType,
                                hash));

        proposals.flush();

        boolean approvalRequired =
                context == null
                        || context.approvalRequired();

        record(
                context,
                situationId,
                "ACTION_PROPOSED",
                context == null
                        ? Instant.now()
                        : context.eventTime(),
                approvalRequired
                        ? "Human approval required before action execution"
                        : "Low-risk action proposed for autonomous execution",
                Map.of(
                        "proposalId",
                        proposal.getId().toString(),
                        "actionType",
                        proposal.getActionType(),
                        "status",
                        proposal.getStatus(),
                        "approvalRequired",
                        approvalRequired,
                        "executorMode",
                        "SIMULATED"));

        return proposal;
    }

    private ExecutionReceipt approveAndExecuteInternal(
            UUID proposalId,
            ApproveActionRequest request,
            ActionTraceContext context) {

        var existing =
                executions.findByIdempotencyKey(
                        request.idempotencyKey());

        if (existing.isPresent()) {

            ActionExecutionEntity execution =
                    existing.get();

            if (!proposalId.equals(
                    execution.getProposalId())) {

                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Idempotency key was already used for a different action proposal");
            }

            return receipt(
                    proposalId,
                    execution);
        }

        ActionProposalEntity proposal =
                proposals.findForUpdate(
                                proposalId)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "Action proposal not found"));

        if (!"PROPOSED".equals(
                proposal.getStatus())) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Action proposal is no longer pending");
        }

        /*
         * Mandatory evidence revalidation immediately before
         * any real/simulated side effect.
         */
        String currentHash =
                evidenceHash.compute(
                        proposal.getSituationId());

        if (!proposal
                .getEvidenceHash()
                .equals(currentHash)) {

            staleCounter.increment();

            record(
                    context,
                    proposal.getSituationId(),
                    "ACTION_BLOCKED_STALE_EVIDENCE",
                    Instant.now(),
                    "Action blocked because operational evidence changed",
                    Map.of(
                            "proposalId",
                            proposalId.toString(),
                            "actionType",
                            proposal.getActionType(),
                            "executorMode",
                            "SIMULATED"));

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Evidence changed; regenerate the recommendation before approval");
        }

        var result =
                executionRouter.execute(
                        proposal,
                        request.idempotencyKey());

        proposal.approve(
                request.approvedBy());

        ActionExecutionEntity execution =
                executions.save(
                        new ActionExecutionEntity(
                                proposalId,
                                request.idempotencyKey(),
                                result.status(),
                                result.externalReference()));

        executions.flush();

        String summary =
                context != null
                        && !context.approvalRequired()
                        ? "Low-risk action executed autonomously after evidence revalidation"
                        : "Approved action executed through configured simulator";

        record(
                context,
                proposal.getSituationId(),
                "ACTION_EXECUTED",
                execution.getExecutedAt(),
                summary,
                Map.of(
                        "proposalId",
                        proposalId.toString(),
                        "executionId",
                        execution.getId().toString(),
                        "actionType",
                        proposal.getActionType(),
                        "status",
                        execution.getStatus(),
                        "externalReference",
                        result.externalReference(),
                        "executorMode",
                        "SIMULATED"));

        return receipt(
                proposalId,
                execution);
    }

    private void validateAction(
            UUID situationId,
            String actionType) {

        if (!situations.existsById(
                situationId)) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Situation not found");
        }

        executionRouter.assertSupported(
                actionType);
    }

    private void record(
            ActionTraceContext context,
            UUID situationId,
            String eventType,
            Instant eventTime,
            String summary,
            Map<String, Object> evidence) {

        if (context == null) {

            traces.record(
                    null,
                    situationId,
                    null,
                    null,
                    "ACT",
                    eventType,
                    eventTime,
                    summary,
                    evidence);

            return;
        }

        traces.record(
                context.sessionId(),
                context.decisionId(),
                situationId,
                context.sourceEventId(),
                context.scopeKey(),
                "ACT",
                eventType,
                eventTime,
                summary,
                evidence);
    }

    private ExecutionReceipt receipt(
            UUID proposalId,
            ActionExecutionEntity execution) {

        return new ExecutionReceipt(
                execution.getId(),
                proposalId,
                execution.getStatus(),
                execution.getExternalReference());
    }

    public record ActionTraceContext(
            UUID sessionId,
            UUID decisionId,
            String sourceEventId,
            String scopeKey,
            Instant eventTime,
            boolean approvalRequired) {

        public ActionTraceContext {

            if (decisionId == null) {
                throw new IllegalArgumentException(
                        "decisionId is required");
            }

            if (eventTime == null) {
                throw new IllegalArgumentException(
                        "eventTime is required");
            }
        }
    }
}