package com.moveiq.api;

import com.moveiq.service.DatasetService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/data")
public class DataController {
    private final DatasetService service;
    public DataController(DatasetService service) { this.service = service; }
    @GetMapping("/status") public DatasetService.DatasetStatus status() { return service.status(); }
}
