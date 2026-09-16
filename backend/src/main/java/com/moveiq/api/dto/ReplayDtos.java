package com.moveiq.api.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public final class ReplayDtos {

    private ReplayDtos() {
    }

    public enum ReplayStatus {
        STOPPED,
        RUNNING,
        PAUSED,
        COMPLETED,
        FAILED
    }

    public record StartReplayRequest(
            Integer speed,
            Instant startAt) {
    }

    public record SetReplaySpeedRequest(
            @NotNull Integer speed) {
    }

    public record ReplayState(
            UUID replaySessionId,
            ReplayStatus status,
            int speed,
            long processed,
            long total,
            double eventsPerSecond,
            Instant replayTime,
            Instant firstEventTime,
            Instant lastEventTime,
            String lastError) {
    }
}