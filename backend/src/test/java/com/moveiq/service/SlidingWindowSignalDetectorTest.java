package com.moveiq.service;

import static org.junit.jupiter.api.Assertions.*;

import com.moveiq.api.dto.MobilityEvent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

class SlidingWindowSignalDetectorTest {

    private static final String DECISION_ID =
            "11111111-1111-1111-1111-111111111111";

    @Test
    void thresholdCrossingUsesAggregateWindowEvidence() {

        Instant occurredAt =
                Instant.parse(
                        "2026-07-15T02:00:00Z");

        FakeRedisTemplate redis =
                new FakeRedisTemplate(
                        List.of(
                                3L,
                                66L,
                                81L,
                                1L,
                                1L,
                                occurredAt.toEpochMilli(),
                                bytes("evt-3"),
                                bytes(DECISION_ID)));

        SlidingWindowSignalDetector detector =
                detector(redis);

        DetectionEvaluation evaluation =
                detector.evaluate(
                        event(
                                "evt-3",
                                "LATE_ARRIVAL",
                                23,
                                23,
                                occurredAt));

        assertEquals(
                UUID.fromString(
                        DECISION_ID),
                evaluation.decisionId());

        assertEquals(
                3,
                evaluation.windowEventCount());

        assertEquals(
                3,
                evaluation.threshold());

        assertEquals(
                66,
                evaluation.affectedEmployees());

        assertEquals(
                81,
                evaluation.delayMinutes());

        assertTrue(
                evaluation.thresholdCrossed());

        assertTrue(
                evaluation.includedInWindow());

        assertTrue(
                evaluation.signal()
                        .isPresent());

        assertEquals(
                66,
                evaluation.signal()
                        .orElseThrow()
                        .affectedEmployees());

        assertEquals(
                81,
                evaluation.signal()
                        .orElseThrow()
                        .delayMinutes());

        assertEquals(
                3,
                evaluation.signal()
                        .orElseThrow()
                        .windowEventCount());
    }

    @Test
    void retryOfThresholdOwnerReEmitsSignalEvenWhenRedisAlreadyContainsEvent() {

        Instant occurredAt =
                Instant.parse(
                        "2026-07-15T02:00:00Z");

        FakeRedisTemplate redis =
                new FakeRedisTemplate(
                        List.of(
                                3L,
                                66L,
                                81L,
                                1L,
                                0L,
                                occurredAt.toEpochMilli(),
                                bytes("evt-3"),
                                bytes(DECISION_ID)));

        SlidingWindowSignalDetector detector =
                detector(redis);

        DetectionEvaluation evaluation =
                detector.evaluate(
                        event(
                                "evt-3",
                                "LATE_ARRIVAL",
                                23,
                                23,
                                occurredAt));

        assertEquals(
                UUID.fromString(
                        DECISION_ID),
                evaluation.decisionId());

        assertTrue(
                evaluation.thresholdCrossed());

        assertFalse(
                evaluation.includedInWindow());

        assertTrue(
                evaluation.signal()
                        .isPresent(),
                "threshold owner must survive PostgreSQL rollback/retry");

        assertEquals(
                "evt-3",
                evaluation.signal()
                        .orElseThrow()
                        .sourceEventId());
    }

    @Test
    void laterEventCannotReEmitSignalOwnedByEarlierThresholdEvent() {

        Instant occurredAt =
                Instant.parse(
                        "2026-07-15T02:01:00Z");

        FakeRedisTemplate redis =
                new FakeRedisTemplate(
                        List.of(
                                4L,
                                90L,
                                105L,
                                0L,
                                1L,
                                occurredAt.toEpochMilli(),
                                bytes("evt-3"),
                                bytes(DECISION_ID)));

        SlidingWindowSignalDetector detector =
                detector(redis);

        DetectionEvaluation evaluation =
                detector.evaluate(
                        event(
                                "evt-4",
                                "LATE_ARRIVAL",
                                24,
                                24,
                                occurredAt));

        assertEquals(
                UUID.fromString(
                        DECISION_ID),
                evaluation.decisionId());

        assertFalse(
                evaluation.thresholdCrossed());

        assertTrue(
                evaluation.includedInWindow());

        assertTrue(
                evaluation.signal()
                        .isEmpty());
    }

    @Test
    void windowProgressDoesNotEmitSignalBeforeThreshold() {

        Instant occurredAt =
                Instant.parse(
                        "2026-07-15T02:00:00Z");

        FakeRedisTemplate redis =
                new FakeRedisTemplate(
                        List.of(
                                2L,
                                37L,
                                44L,
                                0L,
                                1L,
                                occurredAt.toEpochMilli(),
                                bytes(""),
                                bytes(DECISION_ID)));

        SlidingWindowSignalDetector detector =
                detector(redis);

        DetectionEvaluation evaluation =
                detector.evaluate(
                        event(
                                "evt-2",
                                "LATE_ARRIVAL",
                                19,
                                22,
                                occurredAt));

        assertEquals(
                UUID.fromString(
                        DECISION_ID),
                evaluation.decisionId());

        assertEquals(
                2,
                evaluation.windowEventCount());

        assertFalse(
                evaluation.thresholdCrossed());

        assertTrue(
                evaluation.signal()
                        .isEmpty());
    }

    @Test
    void lateEventOutsideActiveWindowDoesNotCreateDecision() {

        Instant occurredAt =
                Instant.parse(
                        "2026-07-15T01:00:00Z");

        FakeRedisTemplate redis =
                new FakeRedisTemplate(
                        List.of(
                                0L,
                                0L,
                                0L,
                                0L,
                                0L,
                                Instant.parse(
                                                "2026-07-15T02:00:00Z")
                                        .toEpochMilli(),
                                bytes(""),
                                bytes("")));

        SlidingWindowSignalDetector detector =
                detector(redis);

        DetectionEvaluation evaluation =
                detector.evaluate(
                        event(
                                "late-1",
                                "LATE_ARRIVAL",
                                5,
                                30,
                                occurredAt));

        assertNull(
                evaluation.decisionId());

        assertEquals(
                0,
                evaluation.windowEventCount());

        assertFalse(
                evaluation.includedInWindow());

        assertFalse(
                evaluation.thresholdCrossed());

        assertTrue(
                evaluation.signal()
                        .isEmpty());
    }

    @Test
    void safetyCriticalEventBypassesRedisThresholdAndCreatesDecision() {

        Instant occurredAt =
                Instant.parse(
                        "2026-07-15T02:00:00Z");

        FakeRedisTemplate redis =
                new FakeRedisTemplate(
                        List.of());

        SlidingWindowSignalDetector detector =
                detector(redis);

        DetectionEvaluation evaluation =
                detector.evaluate(
                        event(
                                "panic-1",
                                "PANIC_BUTTON",
                                4,
                                0,
                                occurredAt));

        assertNotNull(
                evaluation.decisionId());

        assertEquals(
                1,
                evaluation.windowEventCount());

        assertEquals(
                1,
                evaluation.threshold());

        assertTrue(
                evaluation.thresholdCrossed());

        assertTrue(
                evaluation.includedInWindow());

        assertEquals(
                0,
                redis.executions);

        assertEquals(
                "SAFETY_RISK",
                evaluation.signal()
                        .orElseThrow()
                        .signalType());
    }

    @Test
    void differentReplaySessionsUseDifferentRedisNamespaces() {

        Instant occurredAt =
                Instant.parse(
                        "2026-07-15T02:00:00Z");

        UUID runA =
                UUID.fromString(
                        "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

        UUID runB =
                UUID.fromString(
                        "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

        FakeRedisTemplate redis =
                new FakeRedisTemplate(
                        List.of(
                                1L,
                                5L,
                                20L,
                                0L,
                                1L,
                                occurredAt.toEpochMilli(),
                                bytes(""),
                                bytes(DECISION_ID)));

        SlidingWindowSignalDetector detector =
                detector(redis);

        detector.evaluate(
                event(
                        "evt-a",
                        "LATE_ARRIVAL",
                        5,
                        20,
                        occurredAt,
                        runA));

        detector.evaluate(
                event(
                        "evt-b",
                        "LATE_ARRIVAL",
                        5,
                        20,
                        occurredAt,
                        runB));

        assertEquals(
                2,
                redis.executedKeys.size());

        assertNotEquals(
                redis.executedKeys
                        .get(0)
                        .get(0),
                redis.executedKeys
                        .get(1)
                        .get(0));
    }

    @Test
    void completedTripDoesNotEnterDisruptionWindow() {

        Instant occurredAt =
                Instant.parse(
                        "2026-07-15T02:00:00Z");

        FakeRedisTemplate redis =
                new FakeRedisTemplate(
                        List.of());

        SlidingWindowSignalDetector detector =
                detector(redis);

        DetectionEvaluation evaluation =
                detector.evaluate(
                        event(
                                "completed-1",
                                "TRIP_COMPLETED",
                                10,
                                0,
                                occurredAt));

        assertNull(
                evaluation.decisionId());

        assertEquals(
                0,
                evaluation.windowEventCount());

        assertFalse(
                evaluation.includedInWindow());

        assertFalse(
                evaluation.thresholdCrossed());

        assertTrue(
                evaluation.signal()
                        .isEmpty());

        assertEquals(
                0,
                redis.executions);
    }

    @Test
    void minorLateArrivalDoesNotEnterDisruptionWindow() {

        Instant occurredAt =
                Instant.parse(
                        "2026-07-15T02:00:00Z");

        FakeRedisTemplate redis =
                new FakeRedisTemplate(
                        List.of());

        SlidingWindowSignalDetector detector =
                detector(redis);

        DetectionEvaluation evaluation =
                detector.evaluate(
                        event(
                                "late-small-1",
                                "LATE_ARRIVAL",
                                10,
                                14,
                                occurredAt));

        assertNull(
                evaluation.decisionId());

        assertEquals(
                0,
                evaluation.windowEventCount());

        assertFalse(
                evaluation.includedInWindow());

        assertFalse(
                evaluation.thresholdCrossed());

        assertTrue(
                evaluation.signal()
                        .isEmpty());

        assertEquals(
                0,
                redis.executions);
    }

    @Test
    void fifteenMinuteLateArrivalEntersDisruptionWindow() {

        Instant occurredAt =
                Instant.parse(
                        "2026-07-15T02:00:00Z");

        FakeRedisTemplate redis =
                new FakeRedisTemplate(
                        List.of(
                                1L,
                                10L,
                                15L,
                                0L,
                                1L,
                                occurredAt.toEpochMilli(),
                                bytes(""),
                                bytes(DECISION_ID)));

        SlidingWindowSignalDetector detector =
                detector(redis);

        DetectionEvaluation evaluation =
                detector.evaluate(
                        event(
                                "late-material-1",
                                "LATE_ARRIVAL",
                                10,
                                15,
                                occurredAt));

        assertEquals(
                UUID.fromString(
                        DECISION_ID),
                evaluation.decisionId());

        assertEquals(
                1,
                evaluation.windowEventCount());

        assertTrue(
                evaluation.includedInWindow());

        assertFalse(
                evaluation.thresholdCrossed());

        assertTrue(
                evaluation.signal()
                        .isEmpty());

        assertEquals(
                1,
                redis.executions);
    }

    @Test
    void rejectsInvalidDetectorConfiguration() {

        FakeRedisTemplate redis =
                new FakeRedisTemplate(
                        List.of());

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SlidingWindowSignalDetector(
                                redis,
                                0,
                                60,
                                3,
                                15));

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SlidingWindowSignalDetector(
                                redis,
                                15,
                                0,
                                3,
                                15));

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SlidingWindowSignalDetector(
                                redis,
                                15,
                                60,
                                0,
                                15));

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SlidingWindowSignalDetector(
                                redis,
                                15,
                                60,
                                3,
                                0));
    }

    private SlidingWindowSignalDetector detector(
            FakeRedisTemplate redis) {

        return new SlidingWindowSignalDetector(
                redis,
                15,
                60,
                3,
                15);
    }

    private static byte[] bytes(
            String value) {

        return value.getBytes(
                java.nio.charset.StandardCharsets.UTF_8);
    }

    private MobilityEvent event(
            String eventId,
            String eventType,
            long affectedEmployees,
            long delayMinutes,
            Instant occurredAt) {

        return event(
                eventId,
                eventType,
                affectedEmployees,
                delayMinutes,
                occurredAt,
                null);
    }

    private MobilityEvent event(
            String eventId,
            String eventType,
            long affectedEmployees,
            long delayMinutes,
            Instant occurredAt,
            UUID replaySessionId) {

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
                occurredAt,
                replaySessionId);
    }

    private static final class FakeRedisTemplate
            extends StringRedisTemplate {

        private final List<Object> result;

        private int executions;

        private final List<List<String>> executedKeys =
                new ArrayList<>();

        private FakeRedisTemplate(
                List<Object> result) {

            this.result =
                    result;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T execute(
                RedisScript<T> script,
                List<String> keys,
                Object... args) {

            executions++;

            executedKeys.add(
                    List.copyOf(
                            keys));

            return (T) result;
        }
    }
}