package com.moveiq.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Backend-owned post-action observation state. The outcome is observational only:
 * replayed historical data can show what followed an action marker, not prove causality.
 */
public record VerificationSnapshot(
        UUID executionId,
        UUID situationId,
        Instant baselineEventTime,
        double baselineAvgDelay,
        long observedSampleSize,
        Double observedAvgDelay,
        Double changePct,
        String outcome,
        String methodologyVersion,
        Instant updatedAt) {}
