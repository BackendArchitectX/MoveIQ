package com.moveiq.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DatasetService {
    private final Path root;
    private final Map<String, List<String>> sources = new LinkedHashMap<>();

    public DatasetService(@Value("${moveiq.data-root}") String dataRoot) {
        this.root = Path.of(dataRoot).toAbsolutePath().normalize();
        sources.put("employees", List.of("emp_Data.csv", "emp_data.csv"));
        sources.put("billing", List.of("bill_data.csv"));
        sources.put("trips_may", List.of("Ride_data_trip-may_2026.csv", "Ride_data _trip-may_2026.csv"));
        sources.put("trips_june", List.of("Ride_data_trip-June_2026.csv", "Ride_data _trip-June_2026.csv"));
        sources.put("trips_july", List.of("Ride_data_trip-July_2026.csv", "Ride_data _trip-July_2026.csv"));
        sources.put("feedback", List.of("trip_feedback.csv"));
        sources.put("alerts", List.of("alerts_data.csv"));
    }

    public DatasetStatus status() {
        Map<String, String> resolved = new LinkedHashMap<>();
        Map<String, List<String>> missing = new LinkedHashMap<>();
        sources.forEach((id, candidates) -> {
            candidates.stream().map(root::resolve).filter(Files::isRegularFile).findFirst()
                    .ifPresentOrElse(path -> resolved.put(id, path.getFileName().toString()), () -> missing.put(id, candidates));
        });
        return new DatasetStatus(root.toString(), resolved, missing, missing.isEmpty());
    }

    public record DatasetStatus(String root, Map<String, String> resolved, Map<String, List<String>> missing, boolean ready) {}
}
