package com.moveiq.api;

import com.moveiq.api.dto.MobilityEvent;
import com.moveiq.messaging.MobilityEventProducer;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/events")
public class EventController {
    private final MobilityEventProducer producer;
    public EventController(MobilityEventProducer producer) { this.producer = producer; }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> publish(@Valid @RequestBody MobilityEvent event) {
        producer.publish(event);
        return Map.of("status", "ACCEPTED", "eventId", event.eventId());
    }
}
