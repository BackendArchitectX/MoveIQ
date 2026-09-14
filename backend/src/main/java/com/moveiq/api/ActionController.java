package com.moveiq.api;

import com.moveiq.api.dto.ActionDtos.*;
import com.moveiq.domain.ActionProposalEntity;
import com.moveiq.service.ActionService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class ActionController {
    private final ActionService service;
    public ActionController(ActionService service) { this.service = service; }

    @PostMapping("/situations/{situationId}/actions")
    public ActionProposalEntity propose(@PathVariable UUID situationId, @Valid @RequestBody ProposeActionRequest request) {
        return service.propose(situationId, request);
    }

    @PostMapping("/actions/{proposalId}/approve")
    public ExecutionReceipt approve(@PathVariable UUID proposalId, @Valid @RequestBody ApproveActionRequest request) {
        return service.approveAndExecute(proposalId, request);
    }
}
