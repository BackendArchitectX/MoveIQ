package com.moveiq.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.moveiq.api.dto.ActionDtos.ApproveActionRequest;
import com.moveiq.api.dto.ActionDtos.ProposeActionRequest;
import com.moveiq.api.dto.OperationTraceEvent;
import com.moveiq.domain.ActionProposalEntity;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DecisionPolicyServiceTest {

    private ActionService actions;
    private DecisionCaseService decisionCases;
    private OperationTraceService traces;
    private DecisionPolicyService policy;

    private UUID sessionId;
    private UUID decisionId;
    private UUID situationId;

    private Instant eventTime;

    @BeforeEach
    void setUp() {

        actions =
                mock(ActionService.class);

        decisionCases =
                mock(DecisionCaseService.class);

        traces =
                mock(OperationTraceService.class);

        policy =
                new DecisionPolicyService(
                        actions,
                        decisionCases,
                        traces);

        sessionId =
                UUID.randomUUID();

        decisionId =
                UUID.randomUUID();

        situationId =
                UUID.randomUUID();

        eventTime =
                Instant.parse(
                        "2026-05-05T07:13:37Z");
    }

    @Test
    void policyMatrixIsDeterministic() {

        var monitor =
                policy.decide(
                        "MONITOR",
                        "HIGH");

        assertEquals(
                DecisionPolicyService.PolicyMode.NO_ACTION,
                monitor.mode());

        assertNull(
                monitor.actionType());

        assertFalse(
                monitor.approvalRequired());

        var lowTrust =
                policy.decide(
                        "INVESTIGATE_SERVICE_PATTERN",
                        "LOW");

        assertEquals(
                DecisionPolicyService.PolicyMode.NO_ACTION,
                lowTrust.mode());

        assertNull(
                lowTrust.actionType());

        var investigate =
                policy.decide(
                        "INVESTIGATE_SERVICE_PATTERN",
                        "MEDIUM");

        assertEquals(
                DecisionPolicyService.PolicyMode.AUTO_EXECUTE,
                investigate.mode());

        assertEquals(
                "NOTIFY_SHIFT_LEAD",
                investigate.actionType());

        assertFalse(
                investigate.approvalRequired());

        var escalate =
                policy.decide(
                        "ESCALATE_CAPACITY_REVIEW",
                        "HIGH");

        assertEquals(
                DecisionPolicyService.PolicyMode.APPROVAL_REQUIRED,
                escalate.mode());

        assertEquals(
                "ESCALATE_VENDOR",
                escalate.actionType());

        assertTrue(
                escalate.approvalRequired());
    }

    @Test
    void monitorRecommendationProducesNoAction() {

        OperationTraceEvent trace =
                reasonTrace(
                        "MONITOR",
                        "HIGH");

        policy.onTrace(
                trace);

        verify(decisionCases)
                .markMonitoring(
                        decisionId);

        verifyNoInteractions(
                actions);

        ArgumentCaptor<String> eventTypes =
                ArgumentCaptor.forClass(
                        String.class);

        verify(
                traces,
                org.mockito.Mockito.times(2))
                .record(
                        eq(sessionId),
                        eq(decisionId),
                        eq(situationId),
                        eq("source-event-3"),
                        eq("vanta-Sea|Denver Office|11:00|LOGIN|LATE_ARRIVAL"),
                        eq("ACT"),
                        eventTypes.capture(),
                        eq(eventTime),
                        anyString(),
                        anyMap());

        assertEquals(
                List.of(
                        "POLICY_MATCHED",
                        "NO_ACTION_REQUIRED"),
                eventTypes.getAllValues());
    }

    @Test
    void lowTrustBlocksOtherwiseActionableRecommendation() {

        OperationTraceEvent trace =
                reasonTrace(
                        "INVESTIGATE_SERVICE_PATTERN",
                        "LOW");

        policy.onTrace(
                trace);

        verify(decisionCases)
                .markMonitoring(
                        decisionId);

        verifyNoInteractions(
                actions);

        verify(
                decisionCases,
                never())
                .markExecuting(
                        decisionId);

        verify(
                decisionCases,
                never())
                .markAwaitingApproval(
                        decisionId);
    }

    @Test
    void investigateWithTrustedEvidenceAutoExecutesShiftNotification() {

        UUID proposalId =
                UUID.randomUUID();

        ActionProposalEntity proposal =
                mock(
                        ActionProposalEntity.class);

        when(proposal.getId())
                .thenReturn(
                        proposalId);

        when(actions.proposeForDecision(
                        eq(situationId),
                        any(ProposeActionRequest.class),
                        any(ActionService.ActionTraceContext.class)))
                .thenReturn(
                        proposal);

        OperationTraceEvent trace =
                reasonTrace(
                        "INVESTIGATE_SERVICE_PATTERN",
                        "MEDIUM");

        policy.onTrace(
                trace);

        verify(actions)
                .proposeForDecision(
                        eq(situationId),
                        argThat(
                                request ->
                                        "NOTIFY_SHIFT_LEAD"
                                                .equals(
                                                        request.actionType())),
                        argThat(
                                context ->
                                        sessionId.equals(
                                                context.sessionId())
                                                && decisionId.equals(
                                                context.decisionId())
                                                && !context.approvalRequired()));

        verify(decisionCases)
                .markExecuting(
                        decisionId);

        verify(actions)
                .approveAndExecuteForDecision(
                        eq(proposalId),
                        argThat(
                                request ->
                                        "MoveIQ autonomous policy"
                                                .equals(
                                                        request.approvedBy())
                                                && (
                                                        "moveiq-policy:"
                                                                + decisionId
                                                                + ":NOTIFY_SHIFT_LEAD")
                                                        .equals(
                                                                request.idempotencyKey())),
                        argThat(
                                context ->
                                        sessionId.equals(
                                                context.sessionId())
                                                && decisionId.equals(
                                                context.decisionId())
                                                && !context.approvalRequired()));

        verify(decisionCases)
                .markVerifying(
                        decisionId);

        verify(
                decisionCases,
                never())
                .markAwaitingApproval(
                        decisionId);
    }

    @Test
    void capacityEscalationRequiresHumanApproval() {

        ActionProposalEntity proposal =
                mock(
                        ActionProposalEntity.class);

        when(actions.proposeForDecision(
                        eq(situationId),
                        any(ProposeActionRequest.class),
                        any(ActionService.ActionTraceContext.class)))
                .thenReturn(
                        proposal);

        OperationTraceEvent trace =
                reasonTrace(
                        "ESCALATE_CAPACITY_REVIEW",
                        "HIGH");

        policy.onTrace(
                trace);

        verify(actions)
                .proposeForDecision(
                        eq(situationId),
                        argThat(
                                request ->
                                        "ESCALATE_VENDOR"
                                                .equals(
                                                        request.actionType())),
                        argThat(
                                context ->
                                        sessionId.equals(
                                                context.sessionId())
                                                && decisionId.equals(
                                                context.decisionId())
                                                && context.approvalRequired()));

        verify(decisionCases)
                .markAwaitingApproval(
                        decisionId);

        verify(
                actions,
                never())
                .approveAndExecuteForDecision(
                        any(),
                        any(ApproveActionRequest.class),
                        any(ActionService.ActionTraceContext.class));

        verify(
                decisionCases,
                never())
                .markExecuting(
                        decisionId);

        verify(
                decisionCases,
                never())
                .markVerifying(
                        decisionId);
    }

    @Test
    void unrelatedTraceDoesNotInvokePolicy() {

        OperationTraceEvent trace =
                new OperationTraceEvent(
                        10L,
                        sessionId,
                        decisionId,
                        situationId,
                        "source-event-3",
                        "vanta-Sea|Denver Office|11:00|LOGIN|LATE_ARRIVAL",
                        "SENSE",
                        "SIGNAL_DETECTED",
                        eventTime,
                        eventTime,
                        "Signal detected",
                        Map.of());

        policy.onTrace(
                trace);

        verifyNoInteractions(
                actions,
                decisionCases,
                traces);
    }

    private OperationTraceEvent reasonTrace(
            String recommendation,
            String trustStatus) {

        return new OperationTraceEvent(
                100L,
                sessionId,
                decisionId,
                situationId,
                "source-event-3",
                "vanta-Sea|Denver Office|11:00|LOGIN|LATE_ARRIVAL",
                "REASON",
                "REASON_SNAPSHOT_COMPUTED",
                eventTime,
                eventTime,
                "Reasoning snapshot computed",
                Map.of(
                        "recommendation",
                        recommendation,
                        "trustStatus",
                        trustStatus));
    }
}
