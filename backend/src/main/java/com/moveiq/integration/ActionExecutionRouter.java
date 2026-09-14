package com.moveiq.integration;

import com.moveiq.domain.ActionProposalEntity;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class ActionExecutionRouter {
    private final List<ActionExecutor> executors;

    public ActionExecutionRouter(List<ActionExecutor> executors) {
        this.executors = List.copyOf(executors);
    }

    public void assertSupported(String actionType) {
        resolve(actionType);
    }

    public ActionExecutor.ActionExecutionResult execute(ActionProposalEntity proposal, String idempotencyKey) {
        return resolve(proposal.getActionType()).execute(proposal, idempotencyKey);
    }

    private ActionExecutor resolve(String actionType) {
        return executors.stream().filter(e -> e.supports(actionType)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported action type: " + actionType));
    }
}
