package com.moveiq.service;

import com.moveiq.api.dto.ReplayDtos.ReplayState;
import com.moveiq.api.dto.ReplayDtos.ReplayStatus;
import com.moveiq.messaging.MobilityEventProducer;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;

@Service
public class ReplayService {
    private static final int PAGE_SIZE = 500;
    private static final int DEFAULT_SPEED = 20;
    private static final Set<Integer> ALLOWED_SPEEDS = Set.of(1, 5, 20, 100);

    private final Object lock = new Object();
    private final ReplayTripSource source;
    private final MobilityEventProducer producer;
    private final ScheduledExecutorService executor;
    private final Deque<ReplayTripSource.ReplayTrip> buffer = new ArrayDeque<>();

    private ReplayStatus status = ReplayStatus.STOPPED;
    private int speed = DEFAULT_SPEED;
    private long processed;
    private long total;
    private Instant firstEventTime;
    private Instant lastEventTime;
    private Instant replayTime;
    private Instant previousPublishedEventTime;
    private ReplayTripSource.ReplayCursor cursor;
    private ReplayTripSource.ReplayTrip pendingTrip;
    private String lastError;
    private ScheduledFuture<?> scheduled;
    private long generation;
    private long activeStartedNanos;
    private long accumulatedActiveNanos;

    public ReplayService(ReplayTripSource source, MobilityEventProducer producer) {
        this.source = source;
        this.producer = producer;
        this.executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "moveiq-replay");
            thread.setDaemon(true);
            return thread;
        });
    }

    public ReplayState start(Integer requestedSpeed) {
        synchronized (lock) {
            if (status == ReplayStatus.RUNNING || status == ReplayStatus.PAUSED) {
                throw new IllegalStateException("Replay is already active");
            }

            speed = normalizeSpeed(requestedSpeed == null ? DEFAULT_SPEED : requestedSpeed);
            ReplayTripSource.ReplayBounds bounds = source.bounds();
            generation++;
            processed = 0;
            total = bounds.total();
            firstEventTime = bounds.firstEventTime();
            lastEventTime = bounds.lastEventTime();
            replayTime = null;
            previousPublishedEventTime = null;
            cursor = null;
            pendingTrip = null;
            lastError = null;
            buffer.clear();
            accumulatedActiveNanos = 0;
            activeStartedNanos = System.nanoTime();

            if (total == 0) {
                activeStartedNanos = 0;
                status = ReplayStatus.COMPLETED;
                return snapshotLocked();
            }

            status = ReplayStatus.RUNNING;
            advanceLocked(generation);
            return snapshotLocked();
        }
    }

    public ReplayState pause() {
        synchronized (lock) {
            requireStatus(ReplayStatus.RUNNING, "Replay is not running");
            accumulateActiveTimeLocked();
            status = ReplayStatus.PAUSED;
            cancelScheduledLocked();
            return snapshotLocked();
        }
    }

    public ReplayState resume() {
        synchronized (lock) {
            requireStatus(ReplayStatus.PAUSED, "Replay is not paused");
            status = ReplayStatus.RUNNING;
            activeStartedNanos = System.nanoTime();
            advanceLocked(generation);
            return snapshotLocked();
        }
    }

    public ReplayState stop() {
        synchronized (lock) {
            if (status == ReplayStatus.RUNNING) {
                accumulateActiveTimeLocked();
            }
            generation++;
            cancelScheduledLocked();
            status = ReplayStatus.STOPPED;
            buffer.clear();
            pendingTrip = null;
            return snapshotLocked();
        }
    }

    public ReplayState setSpeed(int requestedSpeed) {
        synchronized (lock) {
            speed = normalizeSpeed(requestedSpeed);
            return snapshotLocked();
        }
    }

    public ReplayState state() {
        synchronized (lock) {
            return snapshotLocked();
        }
    }

    private void advanceLocked(long expectedGeneration) {
        if (status != ReplayStatus.RUNNING || generation != expectedGeneration) {
            return;
        }
        if (scheduled != null && !scheduled.isDone()) {
            return;
        }

        if (pendingTrip == null) {
            try {
                pendingTrip = nextTripLocked();
            } catch (RuntimeException e) {
                failLocked(e);
                return;
            }
        }

        if (pendingTrip == null) {
            completeLocked();
            return;
        }

        long delayMillis = replayDelayMillis(
                previousPublishedEventTime,
                pendingTrip.event().occurredAt(),
                speed);

        scheduled = executor.schedule(
                () -> dispatchPending(expectedGeneration),
                delayMillis,
                TimeUnit.MILLISECONDS);
    }

    private void dispatchPending(long expectedGeneration) {
        ReplayTripSource.ReplayTrip trip;
        synchronized (lock) {
            scheduled = null;
            if (status != ReplayStatus.RUNNING || generation != expectedGeneration) {
                return;
            }
            trip = pendingTrip;
            if (trip == null) {
                advanceLocked(expectedGeneration);
                return;
            }
        }

        try {
            producer.publishAsync(trip.event()).whenComplete((result, error) -> {
                synchronized (lock) {
                    if (generation != expectedGeneration) {
                        return;
                    }
                    if (error != null) {
                        failLocked(error);
                        return;
                    }

                    processed++;
                    replayTime = trip.event().occurredAt();
                    previousPublishedEventTime = trip.event().occurredAt();
                    cursor = trip.cursor();
                    pendingTrip = null;

                    if (status == ReplayStatus.RUNNING) {
                        advanceLocked(expectedGeneration);
                    }
                }
            });
        } catch (RuntimeException e) {
            synchronized (lock) {
                if (generation == expectedGeneration) {
                    failLocked(e);
                }
            }
        }
    }

    private ReplayTripSource.ReplayTrip nextTripLocked() {
        if (buffer.isEmpty()) {
            List<ReplayTripSource.ReplayTrip> page = cursor == null
                    ? source.firstPage(PAGE_SIZE)
                    : source.nextPage(cursor, PAGE_SIZE);
            buffer.addAll(page);
        }
        return buffer.pollFirst();
    }

    private long replayDelayMillis(Instant previous, Instant next, int currentSpeed) {
        if (previous == null || !next.isAfter(previous)) {
            return 0;
        }
        long eventMillis = Duration.between(previous, next).toMillis();
        return Math.max(0, eventMillis / currentSpeed);
    }

    private void completeLocked() {
        if (status == ReplayStatus.RUNNING) {
            accumulateActiveTimeLocked();
        }
        status = ReplayStatus.COMPLETED;
        scheduled = null;
        pendingTrip = null;
    }

    private void failLocked(Throwable error) {
        if (status == ReplayStatus.RUNNING) {
            accumulateActiveTimeLocked();
        }
        lastError = rootMessage(error);
        status = ReplayStatus.FAILED;
        cancelScheduledLocked();
    }

    private void accumulateActiveTimeLocked() {
        if (activeStartedNanos != 0) {
            accumulatedActiveNanos += Math.max(0, System.nanoTime() - activeStartedNanos);
            activeStartedNanos = 0;
        }
    }

    private long activeNanosLocked() {
        if (status == ReplayStatus.RUNNING && activeStartedNanos != 0) {
            return accumulatedActiveNanos + Math.max(0, System.nanoTime() - activeStartedNanos);
        }
        return accumulatedActiveNanos;
    }

    private ReplayState snapshotLocked() {
        double seconds = activeNanosLocked() / 1_000_000_000.0;
        double eventsPerSecond = seconds <= 0 ? 0 : processed / seconds;
        return new ReplayState(
                status,
                speed,
                processed,
                total,
                eventsPerSecond,
                replayTime,
                firstEventTime,
                lastEventTime,
                lastError);
    }

    private int normalizeSpeed(int requestedSpeed) {
        if (!ALLOWED_SPEEDS.contains(requestedSpeed)) {
            throw new IllegalArgumentException("Replay speed must be one of 1, 5, 20, 100");
        }
        return requestedSpeed;
    }

    private void requireStatus(ReplayStatus required, String message) {
        if (status != required) {
            throw new IllegalStateException(message);
        }
    }

    private void cancelScheduledLocked() {
        if (scheduled != null) {
            scheduled.cancel(false);
            scheduled = null;
        }
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    @PreDestroy
    public void shutdown() {
        synchronized (lock) {
            generation++;
            cancelScheduledLocked();
            status = ReplayStatus.STOPPED;
            pendingTrip = null;
        }
        executor.shutdownNow();
    }
}
