package com.moveiq.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

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
        @NotNull Instant occurredAt) {}
