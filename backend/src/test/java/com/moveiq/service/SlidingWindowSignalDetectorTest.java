package com.moveiq.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.moveiq.api.dto.MobilityEvent;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

class SlidingWindowSignalDetectorTest {
    @SuppressWarnings("unchecked")
    @Test
    void emitsSignalWhenDistributedWindowReachesThreshold() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ZSetOperations<String, String> zset = mock(ZSetOperations.class);
        when(redis.opsForZSet()).thenReturn(zset);
        when(zset.zCard(anyString())).thenReturn(3L);

        var detector = new SlidingWindowSignalDetector(redis, 15, 60, 3);
        var event = new MobilityEvent(
                "evt-3", "vanta-Aus", 101L, "VEHICLE_STOPPAGE", "Cedar", "03:00",
                "LOGIN", "vendor-a", 12, 20, Instant.parse("2026-07-15T02:20:00Z"));

        var signal = detector.detect(event);

        assertTrue(signal.isPresent());
        assertEquals("MOBILITY_DISRUPTION", signal.get().signalType());
        assertEquals(3, signal.get().windowCount());
        verify(zset).add(anyString(), eq("evt-3"), anyDouble());
        verify(zset).removeRangeByScore(anyString(), anyDouble(), anyDouble());
    }

    @SuppressWarnings("unchecked")
    @Test
    void safetyEventsBypassStatisticalThreshold() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        var detector = new SlidingWindowSignalDetector(redis, 15, 60, 99);
        var event = new MobilityEvent(
                "panic-1", "vanta-Aus", 101L, "PANIC_MOBILE", "Cedar", "03:00",
                "LOGIN", "vendor-a", 1, 0, Instant.parse("2026-07-15T02:20:00Z"));
        assertEquals("SAFETY_RISK", detector.detect(event).orElseThrow().signalType());
        verify(redis, never()).opsForZSet();
    }
}
