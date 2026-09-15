package com.moveiq.api.dto;

import com.moveiq.api.dto.ReplayDtos.ReplayState;
import java.time.Instant;

public record ControlRoomState(
        ReplayState source,
        ReasoningSnapshot reason,
        VerificationSnapshot verify,
        Instant generatedAt) {}
