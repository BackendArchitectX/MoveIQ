package com.moveiq.service;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;

@Service
public class DatasetProfileService {
    private final DatasetService datasets;

    public DatasetProfileService(DatasetService datasets) {
        this.datasets = datasets;
    }

    public Map<String, FileProfile> profile(long maxRowsPerFile) {
        Map<String, FileProfile> result = new LinkedHashMap<>();
        datasets.resolvedFiles().forEach((id, path) -> result.put(id, profileFile(id, path, maxRowsPerFile)));
        return result;
    }

    private FileProfile profileFile(String id, Path path, long limit) {
        long rows = 0;
        Map<String, Long> quality = new LinkedHashMap<>();
        boolean truncated = false;
        try (Reader reader = Files.newBufferedReader(path);
             CSVParser parser = new CSVParser(reader, CSVFormat.DEFAULT.builder()
                     .setHeader().setSkipHeaderRecord(true).setIgnoreSurroundingSpaces(true).build())) {
            Map<String, String> headers = normalizeHeaders(parser.getHeaderMap());
            for (CSVRecord record : parser) {
                if (limit > 0 && rows >= limit) {
                    truncated = true;
                    break;
                }
                rows++;
                inspect(id, record, headers, quality);
            }
            return new FileProfile(path.getFileName().toString(), Files.size(path), rows, truncated, quality);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to profile " + path, e);
        }
    }

    private void inspect(String id, CSVRecord row, Map<String, String> headers, Map<String, Long> quality) {
        if (id.equals("employees")) {
            negative(row, headers, "planned_km", "negative_planned_km", quality);
            negative(row, headers, "traveled_km", "negative_traveled_km", quality);
            if ("0".equals(clean(value(row, headers, "stwid")))) increment(quality, "placeholder_stwid_zero");
        } else if (id.equals("alerts")) {
            if ("false".equalsIgnoreCase(clean(value(row, headers, "severity")))) increment(quality, "invalid_false_severity");
        } else if (id.equals("billing")) {
            Double km = number(value(row, headers, "total_trip_km"));
            if (km != null && km == 0d) increment(quality, "zero_trip_km");
        } else if (id.equals("feedback")) {
            for (String rating : new String[]{"route_rating", "driver_rating", "cab_rating", "safety_rating", "marshal_rating"}) {
                Double value = number(value(row, headers, rating));
                if (value != null && value == 0d) {
                    increment(quality, "rows_with_zero_rating");
                    break;
                }
            }
        } else if (id.startsWith("trips_")) {
            if (longNumber(value(row, headers, "trip_id")) == null) increment(quality, "unparseable_trip_id");
            String delay = clean(value(row, headers, "delay_minutes"));
            if (!delay.isEmpty() && number(delay) == null) increment(quality, "unparseable_delay_minutes");
        }
    }

    private void negative(CSVRecord row, Map<String, String> headers, String column, String metric, Map<String, Long> quality) {
        Double value = number(value(row, headers, column));
        if (value != null && value < 0) increment(quality, metric);
    }

    private Map<String, String> normalizeHeaders(Map<String, Integer> raw) {
        Map<String, String> result = new LinkedHashMap<>();
        raw.keySet().forEach(h -> result.put(h.trim().toLowerCase(Locale.ROOT), h));
        return result;
    }

    private String value(CSVRecord row, Map<String, String> headers, String key) {
        String actual = headers.get(key.toLowerCase(Locale.ROOT));
        return actual == null ? "" : row.get(actual);
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private Double number(String value) {
        String clean = clean(value).replace(",", "");
        if (clean.isEmpty()) return null;
        try { return Double.parseDouble(clean); } catch (NumberFormatException e) { return null; }
    }

    private Long longNumber(String value) {
        String clean = clean(value).replace(",", "");
        if (clean.isEmpty()) return null;
        try { return Long.parseLong(clean); } catch (NumberFormatException e) { return null; }
    }

    private void increment(Map<String, Long> quality, String name) {
        quality.merge(name, 1L, Long::sum);
    }

    public record FileProfile(String file, long fileSizeBytes, long scannedRows, boolean truncated, Map<String, Long> qualityFlags) {}
}
