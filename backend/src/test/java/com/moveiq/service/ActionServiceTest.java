package com.moveiq.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.moveiq.api.dto.ActionDtos.ApproveActionRequest;
import com.moveiq.domain.ActionProposalEntity;
import com.moveiq.repository.ActionExecutionRepository;
import com.moveiq.repository.ActionProposalRepository;
import com.moveiq.repository.SituationRepository;
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
    @Mock RedisIdempotencyService idempotency;

    @Test
    void rejectsApprovalWhenEvidenceChanged() {
        UUID proposalId = UUID.randomUUID();
        var proposal = new ActionProposalEntity(UUID.randomUUID(), "ESCALATE_VENDOR", "old-hash");
        when(proposals.findById(proposalId)).thenReturn(Optional.of(proposal));
        var service = new ActionService(proposals, executions, situations, idempotency);

        assertThrows(ResponseStatusException.class, () -> service.approveAndExecute(
                proposalId, new ApproveActionRequest("manager", "new-hash", "idem-1")));
    }
}
