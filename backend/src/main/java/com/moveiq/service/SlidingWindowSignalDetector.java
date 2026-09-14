package com.moveiq.service;

import com.moveiq.api.dto.DetectedSignal;
import com.moveiq.api.dto.MobilityEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SlidingWindowSignalDetector {
    private final Duration window;
    private final int threshold;
    private final ConcurrentHashMap<String, Deque<Instant>> windows = new ConcurrentHashMap<>();

    public SlidingWindowSignalDetector(
            @Value("${moveiq.detection.window-minutes:15}") long windowMinutes,
            @Value("${moveiq.detection.event-threshold:3}") int threshold) {
        this(Duration.ofMinutes(windowMinutes), threshold);
    }

    public SlidingWindowSignalDetector(Duration window, int threshold) {
        this.window = window; this.threshold = threshold;
    }

    public Optional<DetectedSignal> detect(MobilityEvent event) {
        if (event.eventType().startsWith("PANIC") || "OVERSPEEDING".equals(event.eventType())) {
            return Optional.of(toSignal(event, 1, "SAFETY_RISK"));
        }

        String scope = String.join("|", safe(event.businessUnit()), safe(event.office()), safe(event.shift()), safe(event.direction()), safe(event.eventType()));
        Deque<Instant> deque = windows.computeIfAbsent(scope, ignored -> new ArrayDeque<>());
        int count;
        synchronized (deque) {
            Instant cutoff = event.occurredAt().minus(window);
            while (!deque.isEmpty() && deque.peekFirst().isBefore(cutoff)) deque.removeFirst();
            deque.addLast(event.occurredAt());
            count = deque.size();
        }
        if (count < threshold) return Optional.empty();
        return Optional.of(toSignal(event, count, "MOBILITY_DISRUPTION"));
    }

    private DetectedSignal toSignal(MobilityEvent e, int count, String type) {
        return new DetectedSignal(e.eventId(), e.businessUnit(), type, e.office(), e.shift(), e.direction(), e.affectedEmployees(), e.delayMinutes(), count, Instant.now());
    }
    private static String safe(String value) { return value == null ? "" : value; }
}
