package com.moveiq.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

import com.moveiq.api.dto.MobilityEvent;
import com.moveiq.api.dto.ReplayDtos.ReplayStatus;
import com.moveiq.messaging.MobilityEventProducer;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ReplayServiceTest {

    private ReplayService replay;

    @AfterEach
    void tearDown() {

        if (replay != null) {
            replay.shutdown();
        }
    }

    @Test
    void emptyDatasetCompletesImmediately() {

        ReplayTripSource source =
                mock(
                        ReplayTripSource.class);

        MobilityEventProducer producer =
                mock(
                        MobilityEventProducer.class);

        when(source.bounds())
                .thenReturn(
                        new ReplayTripSource.ReplayBounds(
                                0,
                                null,
                                null));

        replay =
                new ReplayService(
                        source,
                        producer);

        var state =
                replay.start(
                        20);

        assertEquals(
                ReplayStatus.COMPLETED,
                state.status());

        assertEquals(
                0,
                state.processed());

        assertEquals(
                0,
                state.total());

        assertNotNull(
                state.replaySessionId());

        verifyNoInteractions(
                producer);
    }

    @Test
    void rejectsUnsupportedSpeed() {

        ReplayTripSource source =
                mock(
                        ReplayTripSource.class);

        MobilityEventProducer producer =
                mock(
                        MobilityEventProducer.class);

        replay =
                new ReplayService(
                        source,
                        producer);

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        replay.start(
                                10));

        verifyNoInteractions(
                source,
                producer);
    }

    @Test
    void publishesInOrderWithSingleInFlightSend()
            throws Exception {

        ReplayTripSource source =
                mock(
                        ReplayTripSource.class);

        MobilityEventProducer producer =
                mock(
                        MobilityEventProducer.class);

        Instant time =
                Instant.parse(
                        "2026-07-15T02:00:00Z");

        var first =
                trip(
                        "PUNE",
                        1L,
                        time);

        var second =
                trip(
                        "PUNE",
                        2L,
                        time);

        when(source.bounds())
                .thenReturn(
                        new ReplayTripSource.ReplayBounds(
                                2,
                                time,
                                time));

        when(source.firstPage(
                anyInt()))
                .thenReturn(
                        List.of(
                                first,
                                second));

        when(source.nextPage(
                any(),
                anyInt()))
                .thenReturn(
                        List.of());

        when(producer.publishAsync(
                any()))
                .thenReturn(
                        CompletableFuture
                                .completedFuture(
                                        null));

        replay =
                new ReplayService(
                        source,
                        producer);

        replay.start(
                100);

        awaitTerminalState();

        var state =
                replay.state();

        assertEquals(
                ReplayStatus.COMPLETED,
                state.status());

        assertEquals(
                2,
                state.processed());

        assertEquals(
                time,
                state.replayTime());

        assertNotNull(
                state.replaySessionId());

        var sessionId =
                state.replaySessionId();

        var order =
                inOrder(
                        producer);

        order.verify(
                        producer)
                .publishAsync(
                        first.event()
                                .withReplaySessionId(
                                        sessionId));

        order.verify(
                        producer)
                .publishAsync(
                        second.event()
                                .withReplaySessionId(
                                        sessionId));

        verify(
                source,
                times(1))
                .firstPage(
                        anyInt());

        verify(
                source,
                times(1))
                .nextPage(
                        second.cursor(),
                        500);
    }

    @Test
    void pauseAndResumePreservesPendingTrip()
            throws Exception {

        ReplayTripSource source =
                mock(
                        ReplayTripSource.class);

        MobilityEventProducer producer =
                mock(
                        MobilityEventProducer.class);

        Instant firstTime =
                Instant.parse(
                        "2026-07-15T02:00:00Z");

        Instant secondTime =
                firstTime.plusSeconds(
                        100);

        var first =
                trip(
                        "PUNE",
                        1L,
                        firstTime);

        var second =
                trip(
                        "PUNE",
                        2L,
                        secondTime);

        when(source.bounds())
                .thenReturn(
                        new ReplayTripSource.ReplayBounds(
                                2,
                                firstTime,
                                secondTime));

        when(source.firstPage(
                anyInt()))
                .thenReturn(
                        List.of(
                                first,
                                second));

        when(source.nextPage(
                any(),
                anyInt()))
                .thenReturn(
                        List.of());

        when(producer.publishAsync(
                any()))
                .thenReturn(
                        CompletableFuture
                                .completedFuture(
                                        null));

        replay =
                new ReplayService(
                        source,
                        producer);

        replay.start(
                100);

        awaitProcessed(
                1);

        var paused =
                replay.pause();

        assertEquals(
                ReplayStatus.PAUSED,
                paused.status());

        Thread.sleep(
                50);

        assertEquals(
                1,
                replay.state()
                        .processed());

        replay.resume();

        awaitTerminalState();

        assertEquals(
                2,
                replay.state()
                        .processed());

        verify(
                producer,
                times(2))
                .publishAsync(
                        any());
    }

    @Test
    void startsAtRequestedEventTimeWithoutScanningEarlierPages()
            throws Exception {

        ReplayTripSource source =
                mock(
                        ReplayTripSource.class);

        MobilityEventProducer producer =
                mock(
                        MobilityEventProducer.class);

        Instant startAt =
                Instant.parse(
                        "2026-05-05T07:00:00Z");

        Instant firstTime =
                Instant.parse(
                        "2026-05-05T07:01:00Z");

        Instant secondTime =
                Instant.parse(
                        "2026-05-05T07:02:00Z");

        var first =
                trip(
                        "vanta-Sea",
                        100L,
                        firstTime);

        var second =
                trip(
                        "vanta-Sea",
                        101L,
                        secondTime);

        when(source.boundsAtOrAfter(
                startAt))
                .thenReturn(
                        new ReplayTripSource.ReplayBounds(
                                2,
                                firstTime,
                                secondTime));

        when(source.firstPageAtOrAfter(
                startAt,
                500))
                .thenReturn(
                        List.of(
                                first,
                                second));

        when(source.nextPage(
                second.cursor(),
                500))
                .thenReturn(
                        List.of());

        when(producer.publishAsync(
                any()))
                .thenReturn(
                        CompletableFuture
                                .completedFuture(
                                        null));

        replay =
                new ReplayService(
                        source,
                        producer);

        var started =
                replay.start(
                        100,
                        startAt);

        assertEquals(
                ReplayStatus.RUNNING,
                started.status());

        assertEquals(
                2,
                started.total());

        assertEquals(
                firstTime,
                started.firstEventTime());

        assertEquals(
                secondTime,
                started.lastEventTime());

        assertNotNull(
                started.replaySessionId());

        awaitTerminalState();

        assertEquals(
                2,
                replay.state()
                        .processed());

        assertEquals(
                secondTime,
                replay.state()
                        .replayTime());

        verify(
                source,
                times(1))
                .boundsAtOrAfter(
                        startAt);

        verify(
                source,
                times(1))
                .firstPageAtOrAfter(
                        startAt,
                        500);

        verify(
                source,
                never())
                .bounds();

        verify(
                source,
                never())
                .firstPage(
                        anyInt());

        var sessionId =
                replay.state()
                        .replaySessionId();

        var order =
                inOrder(
                        producer);

        order.verify(
                        producer)
                .publishAsync(
                        first.event()
                                .withReplaySessionId(
                                        sessionId));

        order.verify(
                        producer)
                .publishAsync(
                        second.event()
                                .withReplaySessionId(
                                        sessionId));
    }

    @Test
    void seekPastEndCompletesImmediately() {

        ReplayTripSource source =
                mock(
                        ReplayTripSource.class);

        MobilityEventProducer producer =
                mock(
                        MobilityEventProducer.class);

        Instant startAt =
                Instant.parse(
                        "2030-01-01T00:00:00Z");

        when(source.boundsAtOrAfter(
                startAt))
                .thenReturn(
                        new ReplayTripSource.ReplayBounds(
                                0,
                                null,
                                null));

        replay =
                new ReplayService(
                        source,
                        producer);

        var state =
                replay.start(
                        100,
                        startAt);

        assertEquals(
                ReplayStatus.COMPLETED,
                state.status());

        assertEquals(
                0,
                state.total());

        assertEquals(
                0,
                state.processed());

        assertNull(
                state.firstEventTime());

        assertNull(
                state.lastEventTime());

        assertNotNull(
                state.replaySessionId());

        verify(
                source,
                times(1))
                .boundsAtOrAfter(
                        startAt);

        verify(
                source,
                never())
                .firstPageAtOrAfter(
                        any(),
                        anyInt());

        verifyNoInteractions(
                producer);
    }

    private ReplayTripSource.ReplayTrip trip(
            String unit,
            long tripId,
            Instant time) {

        long epoch =
                time.getEpochSecond();

        MobilityEvent event =
                new MobilityEvent(
                        "replay-"
                                + unit
                                + "-"
                                + tripId
                                + "-"
                                + epoch,
                        unit,
                        tripId,
                        "LATE_ARRIVAL",
                        "HINJEWADI",
                        "09:00",
                        "IN",
                        "Vendor-A",
                        10,
                        20,
                        time);

        return new ReplayTripSource.ReplayTrip(
                new ReplayTripSource.ReplayCursor(
                        epoch,
                        unit,
                        tripId),
                event);
    }

    private void awaitProcessed(
            long expected)
            throws InterruptedException {

        long deadline =
                System.nanoTime()
                        + 2_000_000_000L;

        while (System.nanoTime()
                < deadline
                && replay.state()
                .processed()
                < expected) {

            Thread.sleep(
                    5);
        }

        assertTrue(
                replay.state()
                        .processed()
                        >= expected,
                "Timed out waiting for replay progress");
    }

    private void awaitTerminalState()
            throws InterruptedException {

        long deadline =
                System.nanoTime()
                        + 3_000_000_000L;

        while (System.nanoTime()
                < deadline) {

            ReplayStatus status =
                    replay.state()
                            .status();

            if (status
                    == ReplayStatus.COMPLETED
                    || status
                    == ReplayStatus.FAILED) {

                return;
            }

            Thread.sleep(
                    5);
        }

        fail(
                "Timed out waiting for replay to finish");
    }
}