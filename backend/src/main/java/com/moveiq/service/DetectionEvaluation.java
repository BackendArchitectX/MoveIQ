package com.moveiq.service;

import com.moveiq.api.dto.DetectedSignal;
import java.util.Optional;
import java.util.UUID;

/**
 * Immutable result of evaluating one mobility event against the distributed detection window.
 *
 * <p>The decisionId correlates the complete operational episode across
 * SENSE -> REASON -> ACT -> VERIFY.
 *
 * <p>The dashboard consumes every evaluation, not only threshold crossings,
 * so it can render backend-derived progress such as 1/3 -> 2/3 -> 3/3
 * without inventing browser state.
 */
public record DetectionEvaluation(
        UUID decisionId,
        int windowEventCount,
        int threshold,
        long affectedEmployees,
        long delayMinutes,
        boolean thresholdCrossed,
        boolean includedInWindow,
        long watermarkEpochMillis,
        Optional<DetectedSignal> signal) {

    public DetectionEvaluation {
        signal = signal == null ? Optional.empty() : signal;
    }
}