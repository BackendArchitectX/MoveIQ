package com.moveiq.api;

import com.moveiq.api.dto.OperationTraceEvent;
import com.moveiq.service.LiveTraceBroadcaster;
import com.moveiq.service.OperationTraceQueryService;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/live")
public class LiveStreamController {

    private final LiveTraceBroadcaster broadcaster;
    private final OperationTraceQueryService traces;

    public LiveStreamController(
            LiveTraceBroadcaster broadcaster,
            OperationTraceQueryService traces) {
        this.broadcaster = broadcaster;
        this.traces = traces;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
        return broadcaster.subscribe(parseCursor(lastEventId));
    }

    @GetMapping("/history")
    public List<OperationTraceEvent> history(
            @RequestParam(defaultValue = "0") long after,
            @RequestParam(defaultValue = "100") int limit) {
        return traces.after(after, limit);
    }

    private long parseCursor(String lastEventId) {
        if (lastEventId == null || lastEventId.isBlank()) return -1L;
        try {
            return Math.max(0L, Long.parseLong(lastEventId));
        } catch (NumberFormatException ignored) {
            return -1L;
        }
    }
}
