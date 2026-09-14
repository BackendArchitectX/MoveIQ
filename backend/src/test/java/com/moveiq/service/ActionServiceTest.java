package com.moveiq.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import com.moveiq.api.dto.ActionDtos.ApproveActionRequest;
import com.moveiq.domain.ActionProposalEntity;
import com.moveiq.integration.ActionExecutionRouter;
import com.moveiq.repository.ActionExecutionRepository;
import com.moveiq.repository.ActionProposalRepository;
import com.moveiq.repository.SituationRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ActionServiceTest {
    @Mock ActionProposalRepository proposals;
    @Mock ActionExecutionRepository executions;
    @Mock SituationRepository situations;
    @Mock EvidenceHashService evidenceHash;
    @Mock ActionExecutionRouter router;

    @Test
    void rejectsApprovalWhenFreshEvidenceChanged() {
        UUID proposalId = UUID.randomUUID();
        UUID situationId = UUID.randomUUID();
        var proposal = new ActionProposalEntity(situationId, "ESCALATE_VENDOR", "old-hash");
        when(executions.findByIdempotencyKey("idem-1")).thenReturn(Optional.empty());
        when(proposals.findForUpdate(proposalId)).thenReturn(Optional.of(proposal));
        when(evidenceHash.compute(situationId)).thenReturn("new-hash");

        var service = new ActionService(
                proposals, executions, situations, evidenceHash, router, new SimpleMeterRegistry());

        assertThrows(ResponseStatusException.class, () -> service.approveAndExecute(
                proposalId, new ApproveActionRequest("manager", "idem-1")));
        verifyNoInteractions(router);
    }
}
