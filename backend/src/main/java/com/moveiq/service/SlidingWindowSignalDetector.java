package com.moveiq.service;

import com.moveiq.api.dto.DetectedSignal;
import com.moveiq.api.dto.MobilityEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

@Service
public class SlidingWindowSignalDetector {
    private final StringRedisTemplate redis;
    private final Duration window;
    private final Duration keyTtl;
    private final int threshold;

    public SlidingWindowSignalDetector(
            StringRedisTemplate redis,
            @Value("${moveiq.detection.window-minutes:15}") long windowMinutes,
            @Value("${moveiq.detection.redis-ttl-minutes:60}") long ttlMinutes,
            @Value("${moveiq.detection.event-threshold:3}") int threshold) {
        this.redis = redis;
        this.window = Duration.ofMinutes(windowMinutes);
        this.keyTtl = Duration.ofMinutes(ttlMinutes);
        this.threshold = threshold;
    }

    public Optional<DetectedSignal> detect(MobilityEvent event) {
        if (isSafetyCritical(event.eventType())) {
            return Optional.of(toSignal(event, 1, "SAFETY_RISK"));
        }

        String key = windowKey(event);
        ZSetOperations<String, String> zset = redis.opsForZSet();
        double score = event.occurredAt().toEpochMilli();
        double cutoff = event.occurredAt().minus(window).toEpochMilli();

        // eventId is the member: duplicate delivery is idempotent at the Redis layer too.
        zset.add(key, event.eventId(), score);
        zset.removeRangeByScore(key, Double.NEGATIVE_INFINITY, cutoff - 1);
        Long count = zset.zCard(key);
        redis.expire(key, keyTtl);

        long observed = count == null ? 0L : count;
        if (observed < threshold) return Optional.empty();
        return Optional.of(toSignal(event, observed, "MOBILITY_DISRUPTION"));
    }

    private boolean isSafetyCritical(String type) {
        return type != null && (type.startsWith("PANIC") || "OVERSPEEDING".equals(type));
    }

    private String windowKey(MobilityEvent e) {
        return "moveiq:window:" + String.join(":",
                safe(e.businessUnit()), safe(e.office()), safe(e.shift()), safe(e.direction()), safe(e.eventType()));
    }

    private DetectedSignal toSignal(MobilityEvent e, long count, String type) {
        int boundedCount = count > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) count;
        return new DetectedSignal(
                e.eventId(), e.businessUnit(), type, e.office(), e.shift(), e.direction(),
                e.affectedEmployees(), e.delayMinutes(), boundedCount, Instant.now());
    }

    private static String safe(String value) {
        return value == null ? "_" : value.replace(':', '_');
    }
}
