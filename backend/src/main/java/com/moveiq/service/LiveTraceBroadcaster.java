package com.moveiq.service;

import com.moveiq.api.dto.OperationTraceEvent;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class LiveTraceBroadcaster {

    private static final int REPLAY_PAGE_SIZE = 500;
    private final Set<SseEmitter> emitters = ConcurrentHashMap.newKeySet();
    private final Object deliveryLock = new Object();
    private final OperationTraceQueryService traces;

    public LiveTraceBroadcaster(OperationTraceQueryService traces) {
        this.traces = traces;
    }

    public SseEmitter subscribe(long afterSequence) {
        SseEmitter emitter = new SseEmitter(30L * 60L * 1000L);
        registerCleanup(emitter);
        try {
            synchronized (deliveryLock) {
                long through = traces.latestSequence();
                long cursor = Math.max(0L, afterSequence);
                while (cursor < through) {
                    List<OperationTraceEvent> page =
                            traces.afterThrough(cursor, through, REPLAY_PAGE_SIZE);
                    if (page.isEmpty()) break;
                    for (OperationTraceEvent trace : page) sendTrace(emitter, trace);
                    cursor = page.get(page.size() - 1).sequence();
                }
                emitters.add(emitter);
                emitter.send(SseEmitter.event().name("connected").data(Map.of(
                        "status", "CONNECTED",
                        "at", Instant.now().toString(),
                        "replayedThrough", through)));
            }
        } catch (IOException | IllegalStateException e) {
            emitters.remove(emitter);
            emitter.completeWithError(e);
        }
        return emitter;
    }

    private void registerCleanup(SseEmitter emitter) {
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(error -> emitters.remove(emitter));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onTrace(OperationTraceEvent trace) {
        synchronized (deliveryLock) {
            for (SseEmitter emitter : emitters) {
                try {
                    sendTrace(emitter, trace);
                } catch (IOException | IllegalStateException e) {
                    emitters.remove(emitter);
                }
            }
        }
    }

    private void sendTrace(SseEmitter emitter, OperationTraceEvent trace) throws IOException {
        emitter.send(SseEmitter.event()
                .id(Long.toString(trace.sequence()))
                .name("trace")
                .data(trace));
    }

    @Scheduled(fixedDelay = 15000)
    public void heartbeat() {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("heartbeat").data(Map.of(
                        "at", Instant.now().toString())));
            } catch (IOException | IllegalStateException e) {
                emitters.remove(emitter);
            }
        }
    }
}
