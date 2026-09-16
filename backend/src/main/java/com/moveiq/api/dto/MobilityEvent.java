package com.moveiq.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record MobilityEvent(
        @NotBlank String eventId,
        @NotBlank String businessUnit,
        Long tripId,
        @NotBlank String eventType,
        String office,
        String shift,
        String direction,
        String vendor,
        long affectedEmployees,
        long delayMinutes,
        @NotNull Instant occurredAt,
        UUID replaySessionId) {

    // Keeps existing non-replay callers/tests source-compatible.
    public MobilityEvent(
            String eventId,
            String businessUnit,
            Long tripId,
            String eventType,
            String office,
            String shift,
            String direction,
            String vendor,
            long affectedEmployees,
            long delayMinutes,
            Instant occurredAt) {

        this(
                eventId,
                businessUnit,
                tripId,
                eventType,
                office,
                shift,
                direction,
                vendor,
                affectedEmployees,
                delayMinutes,
                occurredAt,
                null);
    }

    public MobilityEvent withReplaySessionId(UUID sessionId) {
        return new MobilityEvent(
                eventId,
                businessUnit,
                tripId,
                eventType,
                office,
                shift,
                direction,
                vendor,
                affectedEmployees,
                delayMinutes,
                occurredAt,
                sessionId);
    }
}