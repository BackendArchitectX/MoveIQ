package com.moveiq.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record DecisionDossier(
        DecisionCase caseInfo,
        Sense sense,
        Reason reason,
        Act act,
        Verify verify,
        List<OperationTraceEvent> proof,
        Long latestSequence,
        Instant generatedAt) {

    public record DecisionCase(
            UUID decisionId,
            UUID replaySessionId,
            String status,
            String scopeKey,
            String triggerEventId,
            UUID situationId,
            Instant openedEventTime,
            Instant detectedEventTime,
            Instant closedEventTime,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record Sense(
            String state,
            Integer windowEventCount,
            Integer detectedWindowEventCount,
            Integer threshold,
            Long affectedEmployeeTrips,
            Long delayMinutesSum,
            Double averageDelayMinutes,
            boolean thresholdCrossed,
            Long watermarkEpochMillis,
            List<String> sourceEventIds) {
    }

    public record Reason(
            String state,
            Double currentAvgDelay,
            Double baselineAvgDelay,
            Double deltaPct,
            Integer currentSampleSize,
            Integer baselineSampleSize,
            Double coveragePct,
            String trustStatus,
            String recommendation,
            String methodologyVersion,
            String dataCutoff,
            Boolean futureDataExcluded) {
    }

    public record Act(
            String state,
            String latestEventType,
            Map<String, Object> evidence) {
    }

    public record Verify(
            String state,
            String latestEventType,
            Map<String, Object> evidence) {
    }
}