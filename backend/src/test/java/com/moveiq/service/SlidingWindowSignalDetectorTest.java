package com.moveiq.service;

import static org.junit.jupiter.api.Assertions.*;

import com.moveiq.api.dto.MobilityEvent;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

class SlidingWindowSignalDetectorTest {

    @Test
    void thresholdCrossingUsesAggregateWindowEvidence() {
        Instant occurredAt = Instant.parse("2026-07-15T02:00:00Z");
        FakeRedisTemplate redis = new FakeRedisTemplate(List.of(
                3L,
                66L,
                81L,
                1L,
                1L,
                occurredAt.toEpochMilli()));
        SlidingWindowSignalDetector detector = new SlidingWindowSignalDetector(redis, 15, 60, 3);

        DetectionEvaluation evaluation = detector.evaluate(event("evt-3", "LATE_ARRIVAL", 23, 23, occurredAt));

        assertEquals(3, evaluation.windowEventCount());
        assertEquals(3, evaluation.threshold());
        assertEquals(66, evaluation.affectedEmployees());
        assertEquals(81, evaluation.delayMinutes());
        assertTrue(evaluation.thresholdCrossed());
        assertTrue(evaluation.includedInWindow());
        assertTrue(evaluation.signal().isPresent());
        assertEquals(66, evaluation.signal().orElseThrow().affectedEmployees());
        assertEquals(81, evaluation.signal().orElseThrow().delayMinutes());
        assertEquals(3, evaluation.signal().orElseThrow().windowEventCount());
    }

    @Test
    void windowProgressDoesNotEmitSignalBeforeThreshold() {
        Instant occurredAt = Instant.parse("2026-07-15T02:00:00Z");
        FakeRedisTemplate redis = new FakeRedisTemplate(List.of(
                2L,
                37L,
                44L,
                0L,
                1L,
                occurredAt.toEpochMilli()));
        SlidingWindowSignalDetector detector = new SlidingWindowSignalDetector(redis, 15, 60, 3);

        DetectionEvaluation evaluation = detector.evaluate(event("evt-2", "LATE_ARRIVAL", 19, 22, occurredAt));

        assertEquals(2, evaluation.windowEventCount());
        assertFalse(evaluation.thresholdCrossed());
        assertTrue(evaluation.signal().isEmpty());
    }

    @Test
    void safetyCriticalEventBypassesRedisThreshold() {
        Instant occurredAt = Instant.parse("2026-07-15T02:00:00Z");
        FakeRedisTemplate redis = new FakeRedisTemplate(List.of());
        SlidingWindowSignalDetector detector = new SlidingWindowSignalDetector(redis, 15, 60, 3);

        DetectionEvaluation evaluation = detector.evaluate(event("panic-1", "PANIC_BUTTON", 4, 0, occurredAt));

        assertEquals(1, evaluation.windowEventCount());
        assertEquals(1, evaluation.threshold());
        assertTrue(evaluation.thresholdCrossed());
        assertEquals(0, redis.executions);
        assertEquals("SAFETY_RISK", evaluation.signal().orElseThrow().signalType());
    }

    @Test
    void rejectsInvalidDetectorConfiguration() {
        FakeRedisTemplate redis = new FakeRedisTemplate(List.of());
        assertThrows(IllegalArgumentException.class, () -> new SlidingWindowSignalDetector(redis, 0, 60, 3));
        assertThrows(IllegalArgumentException.class, () -> new SlidingWindowSignalDetector(redis, 15, 0, 3));
        assertThrows(IllegalArgumentException.class, () -> new SlidingWindowSignalDetector(redis, 15, 60, 0));
    }

    private MobilityEvent event(
            String eventId,
            String eventType,
            long affectedEmployees,
            long delayMinutes,
            Instant occurredAt) {
        return new MobilityEvent(
                eventId,
                "PUNE",
                1L,
                eventType,
                "HINJEWADI",
                "09:00",
                "IN",
                "Vendor-A",
                affectedEmployees,
                delayMinutes,
                occurredAt);
    }

    private static final class FakeRedisTemplate extends StringRedisTemplate {
        private final List<Object> result;
        private int executions;

        private FakeRedisTemplate(List<Object> result) {
            this.result = result;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T execute(RedisScript<T> script, List<String> keys, Object... args) {
            executions++;
            return (T) result;
        }
    }
}
