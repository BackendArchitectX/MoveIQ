package com.moveiq.api;

import com.moveiq.service.DatasetProfileService;
import com.moveiq.service.DatasetService;
import com.moveiq.service.TripIngestionService;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/data")
public class DataController {
    private final DatasetService datasets;
    private final DatasetProfileService profiler;
    private final TripIngestionService tripIngestion;

    public DataController(DatasetService datasets, DatasetProfileService profiler, TripIngestionService tripIngestion) {
        this.datasets = datasets;
        this.profiler = profiler;
        this.tripIngestion = tripIngestion;
    }

    @GetMapping("/status")
    public DatasetService.DatasetStatus status() {
        return datasets.status();
    }

    @GetMapping("/profile")
    public Map<String, DatasetProfileService.FileProfile> profile(
            @RequestParam(defaultValue = "100000") long maxRowsPerFile) {
        if (maxRowsPerFile < 0 || maxRowsPerFile > 5_000_000) {
            throw new IllegalArgumentException("maxRowsPerFile must be between 0 and 5,000,000; 0 means full scan");
        }
        return profiler.profile(maxRowsPerFile);
    }

    @PostMapping("/ingest/trips")
    public TripIngestionService.IngestionSummary ingestTrips() {
        return tripIngestion.ingest();
    }
}
