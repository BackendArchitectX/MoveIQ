package com.moveiq.service;

import static org.junit.jupiter.api.Assertions.*;

import com.moveiq.api.dto.MobilityEvent;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SlidingWindowSignalDetectorTest {
    @Test
    void emitsOnlyWhenThresholdReachedWithinWindow() {
        var detector = new SlidingWindowSignalDetector(Duration.ofMinutes(15), 3);
        Instant t = Instant.parse("2026-07-15T02:00:00Z");

        assertTrue(detector.detect(event("e1", t)).isEmpty());
        assertTrue(detector.detect(event("e2", t.plusSeconds(60))).isEmpty());
        var signal = detector.detect(event("e3", t.plusSeconds(120)));

        assertTrue(signal.isPresent());
        assertEquals(3, signal.get().windowEventCount());
        assertEquals("MOBILITY_DISRUPTION", signal.get().signalType());
    }

    @Test
    void safetyEventsBypassStatisticalThreshold() {
        var detector = new SlidingWindowSignalDetector(Duration.ofMinutes(15), 99);
        var event = new MobilityEvent("panic-1", "vanta-Aus", 1L, "PANIC_MOBILE", "Cedar Ridge", "03:00", "LOGIN", "Vendor A", 1, 0, Instant.now());
        assertEquals("SAFETY_RISK", detector.detect(event).orElseThrow().signalType());
    }

    private MobilityEvent event(String id, Instant at) {
        return new MobilityEvent(id, "vanta-Aus", 1L, "VEHICLE_STOPPAGE", "Cedar Ridge", "03:00", "LOGIN", "Vendor A", 5, 12, at);
    }
}
