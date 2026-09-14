package com.moveiq.api.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public final class ReplayDtos {
    private ReplayDtos() {}

    public enum ReplayStatus { STOPPED, RUNNING, PAUSED, COMPLETED, FAILED }

    public record StartReplayRequest(Integer speed) {}

    public record SetReplaySpeedRequest(@NotNull Integer speed) {}

    public record ReplayState(
            ReplayStatus status,
            int speed,
            long processed,
            long total,
            double eventsPerSecond,
            Instant replayTime,
            Instant firstEventTime,
            Instant lastEventTime,
            String lastError) {}
}
