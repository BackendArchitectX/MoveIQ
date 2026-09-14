package com.moveiq.integration;

import com.moveiq.domain.ActionProposalEntity;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class SimulatedActionExecutor implements ActionExecutor {
    @Override
    public boolean supports(String actionType) {
        return "ESCALATE_VENDOR".equals(actionType) || "NOTIFY_SHIFT_LEAD".equals(actionType);
    }

    @Override
    public ActionExecutionResult execute(ActionProposalEntity proposal, String idempotencyKey) {
        // Deterministic reference models a downstream endpoint that honors the same idempotency key.
        UUID reference = UUID.nameUUIDFromBytes((proposal.getId() + ":" + idempotencyKey).getBytes(StandardCharsets.UTF_8));
        return new ActionExecutionResult("EXECUTED", "SIM-" + reference);
    }
}
