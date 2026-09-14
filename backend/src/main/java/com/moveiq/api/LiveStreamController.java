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

    @GetMapping(
            value = "/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return broadcaster.subscribe();
    }

    @GetMapping("/history")
    public List<OperationTraceEvent> history(
            @RequestParam(defaultValue = "0") long after,
            @RequestParam(defaultValue = "100") int limit) {

        return traces.after(after, limit);
    }
}