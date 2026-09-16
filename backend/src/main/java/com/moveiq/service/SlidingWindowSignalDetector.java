package com.moveiq.service;

import com.moveiq.api.dto.DetectedSignal;
import com.moveiq.api.dto.MobilityEvent;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
public class SlidingWindowSignalDetector {

    /**
     * Entire window mutation happens atomically inside Redis.
     *
     * <p>This prevents concurrent Kafka consumers from interleaving:
     *
     * <ul>
     *     <li>watermark advancement</li>
     *     <li>expired-event pruning</li>
     *     <li>event insertion</li>
     *     <li>aggregate evidence calculation</li>
     *     <li>threshold latching</li>
     *     <li>decision-case correlation</li>
     * </ul>
     *
     * <p>The threshold latch stores the event ID that originally crossed
     * the threshold. Redis is not enlisted in the PostgreSQL transaction,
     * so if PostgreSQL rolls back, a Kafka retry of the same event must be
     * able to reproduce the signal.
     *
     * <p>The decision ID identifies one active detection episode. Events
     * 1/3, 2/3 and 3/3 therefore belong to one durable decision chain.
     *
     * <p>The watermark is monotonic. Late events cannot move event-time
     * processing backwards.
     */
    private static final String WINDOW_LUA = """
            local eventId = ARGV[1]
            local eventTime = tonumber(ARGV[2])
            local windowMillis = tonumber(ARGV[3])
            local ttlSeconds = tonumber(ARGV[4])
            local threshold = tonumber(ARGV[5])
            local affected = tonumber(ARGV[6])
            local delay = tonumber(ARGV[7])
            local candidateDecisionId = ARGV[8]

            ------------------------------------------------------------
            -- 1. ADVANCE EVENT-TIME WATERMARK
            ------------------------------------------------------------
            local watermark =
                tonumber(
                    redis.call(
                        'GET',
                        KEYS[4]
                    ) or '0'
                )

            if eventTime > watermark then

                watermark = eventTime

                redis.call(
                    'SET',
                    KEYS[4],
                    tostring(watermark)
                )
            end

            local cutoff =
                watermark - windowMillis

            ------------------------------------------------------------
            -- 2. REMOVE EXPIRED WINDOW MEMBERS + THEIR EVIDENCE
            ------------------------------------------------------------
            local expired =
                redis.call(
                    'ZRANGEBYSCORE',
                    KEYS[1],
                    '-inf',
                    cutoff - 1
                )

            if #expired > 0 then

                redis.call(
                    'HDEL',
                    KEYS[2],
                    unpack(expired)
                )
            end

            redis.call(
                'ZREMRANGEBYSCORE',
                KEYS[1],
                '-inf',
                cutoff - 1
            )

            ------------------------------------------------------------
            -- 3. DETERMINE ACTIVE DECISION EPISODE
            ------------------------------------------------------------
            local countBefore =
                redis.call(
                    'ZCARD',
                    KEYS[1]
                )

            local activeDecisionId =
                redis.call(
                    'GET',
                    KEYS[5]
                ) or ''

            if countBefore == 0 then

                redis.call(
                    'DEL',
                    KEYS[3]
                )

                if eventTime >= cutoff then

                    activeDecisionId =
                        candidateDecisionId

                    redis.call(
                        'SET',
                        KEYS[5],
                        activeDecisionId
                    )

                else

                    activeDecisionId = ''

                    redis.call(
                        'DEL',
                        KEYS[5]
                    )
                end

            elseif activeDecisionId == '' then

                activeDecisionId =
                    candidateDecisionId

                redis.call(
                    'SET',
                    KEYS[5],
                    activeDecisionId
                )
            end

            ------------------------------------------------------------
            -- 4. ADD CURRENT EVENT IDEMPOTENTLY
            ------------------------------------------------------------
            local included = 0

            if eventTime >= cutoff then

                local added =
                    redis.call(
                        'ZADD',
                        KEYS[1],
                        'NX',
                        eventTime,
                        eventId
                    )

                if added == 1 then

                    redis.call(
                        'HSET',
                        KEYS[2],
                        eventId,
                        tostring(affected)
                            .. '|'
                            .. tostring(delay)
                    )

                    included = 1
                end
            end

            ------------------------------------------------------------
            -- 5. CALCULATE CURRENT WINDOW EVIDENCE
            ------------------------------------------------------------
            local count =
                redis.call(
                    'ZCARD',
                    KEYS[1]
                )

            local totals =
                redis.call(
                    'HVALS',
                    KEYS[2]
                )

            local affectedTotal = 0
            local delayTotal = 0

            for _, value in ipairs(totals) do

                local separator =
                    string.find(
                        value,
                        '|',
                        1,
                        true
                    )

                if separator then

                    affectedTotal =
                        affectedTotal
                        + tonumber(
                            string.sub(
                                value,
                                1,
                                separator - 1
                            )
                        )

                    delayTotal =
                        delayTotal
                        + tonumber(
                            string.sub(
                                value,
                                separator + 1
                            )
                        )
                end
            end

            ------------------------------------------------------------
            -- 6. THRESHOLD-CROSSING LATCH
            ------------------------------------------------------------
            local latchOwner =
                redis.call(
                    'GET',
                    KEYS[3]
                ) or ''

            local crossed = 0

            if count >= threshold then

                if latchOwner == '' then

                    latchOwner = eventId

                    redis.call(
                        'SET',
                        KEYS[3],
                        eventId,
                        'EX',
                        ttlSeconds
                    )

                    crossed = 1

                elseif latchOwner == eventId then

                    crossed = 1
                end

            elseif latchOwner ~= '' then

                redis.call(
                    'DEL',
                    KEYS[3]
                )

                latchOwner = ''
            end

            ------------------------------------------------------------
            -- 7. REFRESH TTLs TOGETHER
            ------------------------------------------------------------
            redis.call(
                'EXPIRE',
                KEYS[1],
                ttlSeconds
            )

            redis.call(
                'EXPIRE',
                KEYS[2],
                ttlSeconds
            )

            redis.call(
                'EXPIRE',
                KEYS[4],
                ttlSeconds
            )

            if activeDecisionId ~= '' then

                redis.call(
                    'EXPIRE',
                    KEYS[5],
                    ttlSeconds
                )
            end

            ------------------------------------------------------------
            -- 8. RETURN COMPLETE ATOMIC EVALUATION
            ------------------------------------------------------------
            return {
                count,
                affectedTotal,
                delayTotal,
                crossed,
                included,
                watermark,
                latchOwner,
                activeDecisionId
            }
            """;

    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<List> WINDOW_SCRIPT =
            new DefaultRedisScript<>(
                    WINDOW_LUA,
                    List.class);

    private final StringRedisTemplate redis;
    private final Duration window;
    private final Duration keyTtl;
    private final int threshold;
    private final int minimumDisruptionDelayMinutes;

    public SlidingWindowSignalDetector(
            StringRedisTemplate redis,
            @Value("${moveiq.detection.window-minutes:15}")
            long windowMinutes,
            @Value("${moveiq.detection.redis-ttl-minutes:60}")
            long ttlMinutes,
            @Value("${moveiq.detection.event-threshold:3}")
            int threshold,
            @Value(
                    "${moveiq.detection.minimum-disruption-delay-minutes:15}")
            int minimumDisruptionDelayMinutes) {

        if (windowMinutes <= 0) {
            throw new IllegalArgumentException(
                    "Detection window must be positive");
        }

        if (ttlMinutes <= 0) {
            throw new IllegalArgumentException(
                    "Redis TTL must be positive");
        }

        if (threshold <= 0) {
            throw new IllegalArgumentException(
                    "Detection threshold must be positive");
        }

        if (minimumDisruptionDelayMinutes <= 0) {
            throw new IllegalArgumentException(
                    "Minimum disruption delay must be positive");
        }

        this.redis = redis;
        this.window =
                Duration.ofMinutes(
                        windowMinutes);

        this.keyTtl =
                Duration.ofMinutes(
                        ttlMinutes);

        this.threshold = threshold;

        this.minimumDisruptionDelayMinutes =
                minimumDisruptionDelayMinutes;
    }

    public DetectionEvaluation evaluate(
            MobilityEvent event) {

        /*
         * Safety-critical events never wait for the ordinary
         * three-event disruption threshold.
         */
        if (isSafetyCritical(
                event.eventType())) {

            UUID decisionId =
                    UUID.randomUUID();

            DetectedSignal signal =
                    toSignal(
                            event,
                            1,
                            event.affectedEmployees(),
                            event.delayMinutes(),
                            "SAFETY_RISK");

            return new DetectionEvaluation(
                    decisionId,
                    1,
                    1,
                    event.affectedEmployees(),
                    event.delayMinutes(),
                    true,
                    true,
                    event.occurredAt()
                            .toEpochMilli(),
                    Optional.of(signal));
        }

        /*
         * Ordinary successful trips must not manufacture incidents.
         *
         * For replay LATE_ARRIVAL events, only operationally material
         * delays enter the disruption window.
         *
         * The supplied dataset showed:
         *
         * positive-delay p50 = 7 minutes
         * positive-delay p75 = 14 minutes
         * positive-delay p90 = 25 minutes
         *
         * Therefore the default business threshold is 15 minutes.
         *
         * Other operational event types continue using the existing
         * detector so live VEHICLE_STOPPAGE-style events are not disabled.
         */
        if (!qualifiesForDisruptionWindow(
                event)) {

            return new DetectionEvaluation(
                    null,
                    0,
                    threshold,
                    0,
                    0,
                    false,
                    false,
                    0L,
                    Optional.empty());
        }

        String tag =
                scopeTag(event);

        /*
         * All five keys deliberately contain the same Redis Cluster
         * hash tag.
         */
        List<String> keys =
                List.of(
                        "moveiq:{"
                                + tag
                                + "}:window",

                        "moveiq:{"
                                + tag
                                + "}:evidence",

                        "moveiq:{"
                                + tag
                                + "}:threshold-latch",

                        "moveiq:{"
                                + tag
                                + "}:watermark",

                        "moveiq:{"
                                + tag
                                + "}:active-decision-id");

        /*
         * Redis decides which candidate UUID becomes the active
         * decision ID for this window.
         */
        UUID candidateDecisionId =
                UUID.randomUUID();

        @SuppressWarnings("unchecked")
        List<Object> result =
                redis.execute(
                        WINDOW_SCRIPT,
                        keys,
                        event.eventId(),
                        Long.toString(
                                event.occurredAt()
                                        .toEpochMilli()),
                        Long.toString(
                                window.toMillis()),
                        Long.toString(
                                keyTtl.toSeconds()),
                        Integer.toString(
                                threshold),
                        Long.toString(
                                event.affectedEmployees()),
                        Long.toString(
                                event.delayMinutes()),
                        candidateDecisionId.toString());

        if (result == null
                || result.size() < 8) {

            throw new IllegalStateException(
                    "Redis detection script returned an invalid result");
        }

        long observed =
                asLong(
                        result.get(0));

        long affected =
                asLong(
                        result.get(1));

        long delay =
                asLong(
                        result.get(2));

        boolean crossed =
                asLong(
                        result.get(3))
                        == 1L;

        boolean included =
                asLong(
                        result.get(4))
                        == 1L;

        long watermark =
                asLong(
                        result.get(5));

        String latchOwner =
                asString(
                        result.get(6));

        String decisionValue =
                asString(
                        result.get(7));

        UUID decisionId =
                decisionValue.isBlank()
                        ? null
                        : UUID.fromString(
                        decisionValue);

        /*
         * A PostgreSQL rollback may cause Kafka to retry the same
         * threshold owner after Redis already inserted it.
         *
         * Therefore signal ownership depends on latchOwner rather
         * than the ZADD included flag.
         */
        boolean ownsCrossing =
                crossed
                        && event.eventId()
                        .equals(
                                latchOwner);

        Optional<DetectedSignal> signal =
                ownsCrossing
                        ? Optional.of(
                        toSignal(
                                event,
                                observed,
                                affected,
                                delay,
                                "MOBILITY_DISRUPTION"))
                        : Optional.empty();

        return new DetectionEvaluation(
                decisionId,
                boundedCount(
                        observed),
                threshold,
                affected,
                delay,
                crossed,
                included,
                watermark,
                signal);
    }

    private boolean qualifiesForDisruptionWindow(
            MobilityEvent event) {

        String type =
                event.eventType();

        /*
         * A normal completion is useful provenance, but it is not
         * negative operational evidence.
         */
        if ("TRIP_COMPLETED".equals(
                type)) {

            return false;
        }

        /*
         * ReplayTripSource maps positive reported delays to
         * LATE_ARRIVAL. Only material delays count toward a
         * disruption episode.
         */
        if ("LATE_ARRIVAL".equals(
                type)) {

            return event.delayMinutes()
                    >= minimumDisruptionDelayMinutes;
        }

        /*
         * Preserve detector behavior for other operational signals,
         * for example VEHICLE_STOPPAGE and future live event types.
         */
        return true;
    }

    private boolean isSafetyCritical(
            String type) {

        return type != null
                && (type.startsWith(
                "PANIC")
                || "OVERSPEEDING".equals(
                type));
    }

    private String scopeTag(
            MobilityEvent event) {

        String operationalScope =
                String.join(
                        "|",
                        safe(
                                event.businessUnit()),
                        safe(
                                event.office()),
                        safe(
                                event.shift()),
                        safe(
                                event.direction()),
                        safe(
                                event.eventType()));

        /*
         * Keep normal/live Redis namespaces unchanged.
         *
         * Each historical replay run gets an isolated namespace so
         * START -> STOP -> START cannot inherit the previous run's
         * window, evidence, latch, watermark or active decision ID.
         */
        String scope =
                event.replaySessionId() == null
                        ? operationalScope
                        : "replay|"
                        + event.replaySessionId()
                        + "|"
                        + operationalScope;

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(
                        scope.getBytes(
                                StandardCharsets.UTF_8));
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
                boundedCount(
                        count),
                event.occurredAt());
    }

    private int boundedCount(
            long count) {

        return count
                > Integer.MAX_VALUE
                ? Integer.MAX_VALUE
                : (int) Math.max(
                0L,
                count);
    }

    private long asLong(
            Object value) {

        if (value instanceof Number number) {
            return number.longValue();
        }

        return Long.parseLong(
                String.valueOf(
                        value));
    }

    private String asString(
            Object value) {

        if (value instanceof byte[] bytes) {

            return new String(
                    bytes,
                    StandardCharsets.UTF_8);
        }

        return value == null
                ? ""
                : String.valueOf(
                value);
    }

    private static String safe(
            String value) {

        return value == null
                ? "_"
                : value;
    }
}