package com.moveiq.service;

import com.moveiq.api.dto.ControlRoomState;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class ControlRoomStateService {
    private final ReplayService replay;
    private final ReasoningService reasoning;
    private final VerificationService verification;

    public ControlRoomStateService(
            ReplayService replay,
            ReasoningService reasoning,
            VerificationService verification) {
        this.replay = replay;
        this.reasoning = reasoning;
        this.verification = verification;
    }

    public ControlRoomState state() {
        return new ControlRoomState(
                replay.state(),
                reasoning.latest().orElse(null),
                verification.latest().orElse(null),
                Instant.now());
    }
}
