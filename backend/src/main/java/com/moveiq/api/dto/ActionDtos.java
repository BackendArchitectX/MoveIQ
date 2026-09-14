package com.moveiq.api.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public final class ActionDtos {
    private ActionDtos() {}

    public record ProposeActionRequest(@NotBlank String actionType) {}
    public record ApproveActionRequest(@NotBlank String approvedBy, @NotBlank String idempotencyKey) {}
    public record ExecutionReceipt(UUID executionId, UUID proposalId, String status, String externalReference) {}
}
