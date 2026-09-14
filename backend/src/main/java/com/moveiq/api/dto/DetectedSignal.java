package com.moveiq.api.dto;

import java.time.Instant;

public record DetectedSignal(
        String sourceEventId,
        String businessUnit,
        String signalType,
        String office,
        String shift,
        String direction,
        long affectedEmployees,
        long delayMinutes,
        int windowEventCount,
        Instant detectedAt) {}
