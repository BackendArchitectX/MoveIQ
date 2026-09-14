package com.moveiq.api.dto;

import java.time.Instant;
import java.util.UUID;

public record ReasoningSnapshot(
        UUID situationId,
        String businessUnit,
        String office,
        String shift,
        String direction,
        Instant eventTime,
        double currentAvgDelay,
        Double baselineAvgDelay,
        Double deltaPct,
        long currentSampleSize,
        long baselineSampleSize,
        double coveragePct,
        String trustStatus,
        String recommendation,
        String methodologyVersion,
        Instant computedAt) {}
