package com.moveiq.service;

import com.moveiq.api.dto.ControlRoomState;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class ControlRoomStateService {
    private final ReplayService replay;
    private final ReasoningService reasoning;

    public ControlRoomStateService(ReplayService replay, ReasoningService reasoning) {
        this.replay = replay;
        this.reasoning = reasoning;
    }

    public ControlRoomState state() {
        return new ControlRoomState(
                replay.state(),
                reasoning.latest().orElse(null),
                Instant.now());
    }
}
