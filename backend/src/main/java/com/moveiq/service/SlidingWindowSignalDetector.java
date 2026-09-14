package com.moveiq.service;

import com.moveiq.api.dto.DetectedSignal;
import com.moveiq.api.dto.MobilityEvent;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
public class SlidingWindowSignalDetector {

    /**
     * The entire sliding-window transition is one Redis script so concurrent Kafka consumers cannot
     * interleave add/prune/count/latch operations. All keys use the same Redis Cluster hash tag.
     *
     * <p>The watermark is monotonic. A late event older than the active event-time window is accepted
     * by the ingestion layer but is not allowed to move the detector backwards or leak future events
     * into an earlier window.
     */
    private static final String WINDOW_LUA = """
            local eventId = ARGV[1]
            local eventTime = tonumber(ARGV[2])
            local windowMillis = tonumber(ARGV[3])
            local ttlSeconds = tonumber(ARGV[4])
            local threshold = tonumber(ARGV[5])
            local affected = tonumber(ARGV[6])
            local delay = tonumber(ARGV[7])

            local watermark = tonumber(redis.call('GET', KEYS[4]) or '0')
            if eventTime > watermark then
                watermark = eventTime
                redis.call('SET', KEYS[4], tostring(watermark))
            end

            local cutoff = watermark - windowMillis
            local expired = redis.call('ZRANGEBYSCORE', KEYS[1], '-inf', cutoff - 1)
            if #expired > 0 then
                redis.call('HDEL', KEYS[2], unpack(expired))
            end
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', cutoff - 1)

            local included = 0
            if eventTime >= cutoff then
                local added = redis.call('ZADD', KEYS[1], 'NX', eventTime, eventId)
                if added == 1 then
                    redis.call('HSET', KEYS[2], eventId, tostring(affected) .. '|' .. tostring(delay))
                    included = 1
                end
            end

            local count = redis.call('ZCARD', KEYS[1])
            local totals = redis.call('HVALS', KEYS[2])
            local affectedTotal = 0
            local delayTotal = 0
            for _, value in ipairs(totals) do
                local separator = string.find(value, '|', 1, true)
                if separator then
                    affectedTotal = affectedTotal + tonumber(string.sub(value, 1, separator - 1))
                    delayTotal = delayTotal + tonumber(string.sub(value, separator + 1))
                end
            end

            local latched = redis.call('EXISTS', KEYS[3])
            local crossed = 0
            if count >= threshold then
                if latched == 0 then
                    redis.call('SET', KEYS[3], '1', 'EX', ttlSeconds)
                    crossed = 1
                end
            elseif latched == 1 then
                redis.call('DEL', KEYS[3])
            end

            redis.call('EXPIRE', KEYS[1], ttlSeconds)
            redis.call('EXPIRE', KEYS[2], ttlSeconds)
            redis.call('EXPIRE', KEYS[4], ttlSeconds)

            return {count, affectedTotal, delayTotal, crossed, included, watermark}
            """;

    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<List> WINDOW_SCRIPT =
            new DefaultRedisScript<>(WINDOW_LUA, List.class);

    private final StringRedisTemplate redis;
    private final Duration window;
    private final Duration keyTtl;
    private final int threshold;

    public SlidingWindowSignalDetector(
            StringRedisTemplate redis,
            @Value("${moveiq.detection.window-minutes:15}") long windowMinutes,
            @Value("${moveiq.detection.redis-ttl-minutes:60}") long ttlMinutes,
            @Value("${moveiq.detection.event-threshold:3}") int threshold) {
        if (windowMinutes <= 0) {
            throw new IllegalArgumentException("Detection window must be positive");
        }
        if (ttlMinutes <= 0) {
            throw new IllegalArgumentException("Redis TTL must be positive");
        }
        if (threshold <= 0) {
            throw new IllegalArgumentException("Detection threshold must be positive");
        }

        this.redis = redis;
        this.window = Duration.ofMinutes(windowMinutes);
        this.keyTtl = Duration.ofMinutes(ttlMinutes);
        this.threshold = threshold;
    }

    public DetectionEvaluation evaluate(MobilityEvent event) {
        if (isSafetyCritical(event.eventType())) {
            DetectedSignal signal = toSignal(
                    event,
                    1,
                    event.affectedEmployees(),
                    event.delayMinutes(),
                    "SAFETY_RISK");
            return new DetectionEvaluation(
                    1,
                    1,
                    event.affectedEmployees(),
                    event.delayMinutes(),
                    true,
                    true,
                    event.occurredAt().toEpochMilli(),
                    Optional.of(signal));
        }

        String tag = scopeTag(event);
        List<String> keys = List.of(
                "moveiq:{" + tag + "}:window",
                "moveiq:{" + tag + "}:evidence",
                "moveiq:{" + tag + "}:threshold-latch",
                "moveiq:{" + tag + "}:watermark");

        @SuppressWarnings("unchecked")
        List<Object> result = redis.execute(
                WINDOW_SCRIPT,
                keys,
                event.eventId(),
                Long.toString(event.occurredAt().toEpochMilli()),
                Long.toString(window.toMillis()),
                Long.toString(keyTtl.toSeconds()),
                Integer.toString(threshold),
                Long.toString(event.affectedEmployees()),
                Long.toString(event.delayMinutes()));

        if (result == null || result.size() < 6) {
            throw new IllegalStateException("Redis detection script returned an invalid result");
        }

        long observed = asLong(result.get(0));
        long affected = asLong(result.get(1));
        long delay = asLong(result.get(2));
        boolean crossed = asLong(result.get(3)) == 1L;
        boolean included = asLong(result.get(4)) == 1L;
        long watermark = asLong(result.get(5));

        Optional<DetectedSignal> signal = crossed
                ? Optional.of(toSignal(event, observed, affected, delay, "MOBILITY_DISRUPTION"))
                : Optional.empty();

        return new DetectionEvaluation(
                boundedCount(observed),
                threshold,
                affected,
                delay,
                crossed,
                included,
                watermark,
                signal);
    }

    private boolean isSafetyCritical(String type) {
        return type != null && (type.startsWith("PANIC") || "OVERSPEEDING".equals(type));
    }

    private String scopeTag(MobilityEvent event) {
        String scope = String.join(
                "|",
                safe(event.businessUnit()),
                safe(event.office()),
                safe(event.shift()),
                safe(event.direction()),
                safe(event.eventType()));
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(scope.getBytes(StandardCharsets.UTF_8));
    }

    private DetectedSignal toSignal(
            MobilityEvent event,
            long count,
            long affectedEmployees,
            long delayMinutes,
            String type) {
        return new DetectedSignal(
                event.eventId(),
                event.businessUnit(),
                type,
                event.office(),
                event.shift(),
                event.direction(),
                affectedEmployees,
                delayMinutes,
                boundedCount(count),
                event.occurredAt());
    }

    private int boundedCount(long count) {
        return count > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(0L, count);
    }

    private long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private static String safe(String value) {
        return value == null ? "_" : value;
    }
}
