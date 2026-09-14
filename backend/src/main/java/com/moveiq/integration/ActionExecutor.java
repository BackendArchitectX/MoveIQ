package com.moveiq.integration;

import com.moveiq.domain.ActionProposalEntity;

public interface ActionExecutor {
    boolean supports(String actionType);
    ActionExecutionResult execute(ActionProposalEntity proposal, String idempotencyKey);

    record ActionExecutionResult(String status, String externalReference) {}
}
