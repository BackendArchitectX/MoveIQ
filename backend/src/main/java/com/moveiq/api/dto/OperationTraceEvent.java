package com.moveiq.api.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record OperationTraceEvent(
        long sequence,
        UUID sessionId,
        UUID situationId,
        String sourceEventId,
        String scopeKey,
        String stage,
        String eventType,
        Instant eventTime,
        Instant recordedAt,
        String summary,
        Map<String, Object> evidence) {
}