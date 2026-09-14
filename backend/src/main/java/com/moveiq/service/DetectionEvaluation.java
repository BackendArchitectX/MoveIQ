package com.moveiq.service;

import com.moveiq.api.dto.DetectedSignal;
import java.util.Optional;

/**
 * Immutable result of evaluating one mobility event against the distributed detection window.
 *
 * <p>The dashboard consumes every evaluation, not only threshold crossings, so SENSE can show
 * backend-derived progress such as 1/3 -> 2/3 -> 3/3 without inventing frontend state.
 */
public record DetectionEvaluation(
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
