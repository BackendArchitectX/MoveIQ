package com.moveiq.service;

import com.moveiq.api.dto.ActionDtos.ApproveActionRequest;
import com.moveiq.api.dto.ActionDtos.ProposeActionRequest;
import com.moveiq.api.dto.OperationTraceEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
public class DecisionPolicyService {

    private static final String POLICY_VERSION =
            "decision-policy-v1";

    private static final String SYSTEM_ACTOR =
            "MoveIQ autonomous policy";

    private final ActionService actions;
    private final DecisionCaseService decisionCases;
    private final OperationTraceService traces;

    public DecisionPolicyService(
            ActionService actions,
            DecisionCaseService decisionCases,
            OperationTraceService traces) {

        this.actions = actions;
        this.decisionCases = decisionCases;
        this.traces = traces;
    }

    /**
     * Reasoning must commit before policy execution begins.
     *
     * This keeps SENSE/REASON deterministic and makes ACT a
     * separate retryable transaction.
     */
    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT,
            fallbackExecution = true)
    @Transactional(
            propagation = Propagation.REQUIRES_NEW)
    public void onTrace(
            OperationTraceEvent trace) {

        if (!"REASON_SNAPSHOT_COMPUTED"
                .equals(trace.eventType())) {

            return;
        }

        if (trace.decisionId() == null
                || trace.situationId() == null) {

            return;
        }

        String recommendation =
                value(
                        trace.evidence()
                                .get("recommendation"));

        String trustStatus =
                value(
                        trace.evidence()
                                .get("trustStatus"));

        PolicyDecision policy =
                decide(
                        recommendation,
                        trustStatus);

        recordPolicyMatch(
                trace,
                recommendation,
                trustStatus,
                policy);

        switch (policy.mode()) {

            case NO_ACTION ->
                    noAction(
                            trace,
                            recommendation,
                            trustStatus,
                            policy);

            case AUTO_EXECUTE ->
                    autoExecute(
                            trace,
                            policy);

            case APPROVAL_REQUIRED ->
                    requestApproval(
                            trace,
                            policy);
        }
    }

    PolicyDecision decide(
            String recommendation,
            String trustStatus) {

        if (recommendation == null
                || trustStatus == null) {

            return new PolicyDecision(
                    PolicyMode.NO_ACTION,
                    null,
                    false,
                    "Reasoning output is incomplete");
        }

        if ("MONITOR".equals(
                recommendation)) {

            return new PolicyDecision(
                    PolicyMode.NO_ACTION,
                    null,
                    false,
                    "Reasoning recommends continued monitoring");
        }

        if ("LOW".equals(
                trustStatus)) {

            return new PolicyDecision(
                    PolicyMode.NO_ACTION,
                    null,
                    false,
                    "Evidence trust is too low for intervention");
        }

        if ("INVESTIGATE_SERVICE_PATTERN"
                .equals(recommendation)
                && trustedForAction(
                trustStatus)) {

            return new PolicyDecision(
                    PolicyMode.AUTO_EXECUTE,
                    "NOTIFY_SHIFT_LEAD",
                    false,
                    "Low-risk operational notification can execute autonomously");
        }

        if ("ESCALATE_CAPACITY_REVIEW"
                .equals(recommendation)
                && trustedForAction(
                trustStatus)) {

            return new PolicyDecision(
                    PolicyMode.APPROVAL_REQUIRED,
                    "ESCALATE_VENDOR",
                    true,
                    "Vendor escalation has higher operational impact and requires human approval");
        }

        return new PolicyDecision(
                PolicyMode.NO_ACTION,
                null,
                false,
                "No approved policy exists for this reasoning outcome");
    }

    private void noAction(
            OperationTraceEvent trace,
            String recommendation,
            String trustStatus,
            PolicyDecision policy) {

        decisionCases.markMonitoring(
                trace.decisionId());

        Map<String, Object> evidence =
                new LinkedHashMap<>();

        evidence.put(
                "policyVersion",
                POLICY_VERSION);

        evidence.put(
                "mode",
                policy.mode().name());

        if (recommendation != null) {
            evidence.put(
                    "recommendation",
                    recommendation);
        }

        if (trustStatus != null) {
            evidence.put(
                    "trustStatus",
                    trustStatus);
        }

        evidence.put(
                "reason",
                policy.reason());

        traces.record(
                trace.sessionId(),
                trace.decisionId(),
                trace.situationId(),
                trace.sourceEventId(),
                trace.scopeKey(),
                "ACT",
                "NO_ACTION_REQUIRED",
                trace.eventTime(),
                "Policy chose continued monitoring instead of intervention",
                evidence);
    }

    private void autoExecute(
            OperationTraceEvent trace,
            PolicyDecision policy) {

        recordActionSelected(
                trace,
                policy);

        ActionService.ActionTraceContext context =
                new ActionService.ActionTraceContext(
                        trace.sessionId(),
                        trace.decisionId(),
                        trace.sourceEventId(),
                        trace.scopeKey(),
                        trace.eventTime(),
                        false);

        var proposal =
                actions.proposeForDecision(
                        trace.situationId(),
                        new ProposeActionRequest(
                                policy.actionType()),
                        context);

        decisionCases.markExecuting(
                trace.decisionId());

        String idempotencyKey =
                "moveiq-policy:"
                        + trace.decisionId()
                        + ":"
                        + policy.actionType();

        actions.approveAndExecuteForDecision(
                proposal.getId(),
                new ApproveActionRequest(
                        SYSTEM_ACTOR,
                        idempotencyKey),
                context);

        /*
         * ACTION_EXECUTED is published as an OperationTraceEvent.
         * VerificationService already consumes that event after
         * commit and creates the verification snapshot.
         */
        decisionCases.markVerifying(
                trace.decisionId());
    }

    private void requestApproval(
            OperationTraceEvent trace,
            PolicyDecision policy) {

        recordActionSelected(
                trace,
                policy);

        ActionService.ActionTraceContext context =
                new ActionService.ActionTraceContext(
                        trace.sessionId(),
                        trace.decisionId(),
                        trace.sourceEventId(),
                        trace.scopeKey(),
                        trace.eventTime(),
                        true);

        actions.proposeForDecision(
                trace.situationId(),
                new ProposeActionRequest(
                        policy.actionType()),
                context);

        decisionCases.markAwaitingApproval(
                trace.decisionId());
    }

    private void recordPolicyMatch(
            OperationTraceEvent trace,
            String recommendation,
            String trustStatus,
            PolicyDecision policy) {

        Map<String, Object> evidence =
                new LinkedHashMap<>();

        evidence.put(
                "policyVersion",
                POLICY_VERSION);

        evidence.put(
                "mode",
                policy.mode().name());

        evidence.put(
                "approvalRequired",
                policy.approvalRequired());

        evidence.put(
                "reason",
                policy.reason());

        if (recommendation != null) {
            evidence.put(
                    "recommendation",
                    recommendation);
        }

        if (trustStatus != null) {
            evidence.put(
                    "trustStatus",
                    trustStatus);
        }

        if (policy.actionType() != null) {
            evidence.put(
                    "actionType",
                    policy.actionType());
        }

        traces.record(
                trace.sessionId(),
                trace.decisionId(),
                trace.situationId(),
                trace.sourceEventId(),
                trace.scopeKey(),
                "ACT",
                "POLICY_MATCHED",
                trace.eventTime(),
                "Autonomous decision policy evaluated committed reasoning",
                evidence);
    }

    private void recordActionSelected(
            OperationTraceEvent trace,
            PolicyDecision policy) {

        traces.record(
                trace.sessionId(),
                trace.decisionId(),
                trace.situationId(),
                trace.sourceEventId(),
                trace.scopeKey(),
                "ACT",
                "ACTION_SELECTED",
                trace.eventTime(),
                policy.approvalRequired()
                        ? "Higher-impact action selected for human approval"
                        : "Low-risk action selected for autonomous execution",
                Map.of(
                        "policyVersion",
                        POLICY_VERSION,
                        "mode",
                        policy.mode().name(),
                        "actionType",
                        policy.actionType(),
                        "approvalRequired",
                        policy.approvalRequired(),
                        "reason",
                        policy.reason()));
    }

    private boolean trustedForAction(
            String trustStatus) {

        return "MEDIUM".equals(
                trustStatus)
                || "HIGH".equals(
                trustStatus);
    }

    private String value(
            Object value) {

        return value == null
                ? null
                : value.toString();
    }

    enum PolicyMode {
        NO_ACTION,
        AUTO_EXECUTE,
        APPROVAL_REQUIRED
    }

    record PolicyDecision(
            PolicyMode mode,
            String actionType,
            boolean approvalRequired,
            String reason) {
    }
}