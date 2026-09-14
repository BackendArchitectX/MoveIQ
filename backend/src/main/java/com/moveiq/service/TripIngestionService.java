package com.moveiq.service;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class TripIngestionService {
    private static final String UPSERT = """
            INSERT INTO moveiq.trip(
                business_unit, trip_id, trip_date, office, shift_type, trip_direction, vendor_id,
                planned_start_epoch, planned_end_epoch, actual_start_epoch, actual_end_epoch,
                reported_delay_minutes, planned_employee_cnt, actual_employee_cnt, noshow_cnt, version)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
            ON CONFLICT (business_unit, trip_id) DO UPDATE SET
                trip_date = EXCLUDED.trip_date,
                office = EXCLUDED.office,
                shift_type = EXCLUDED.shift_type,
                trip_direction = EXCLUDED.trip_direction,
                vendor_id = EXCLUDED.vendor_id,
                planned_start_epoch = EXCLUDED.planned_start_epoch,
                planned_end_epoch = EXCLUDED.planned_end_epoch,
                actual_start_epoch = EXCLUDED.actual_start_epoch,
                actual_end_epoch = EXCLUDED.actual_end_epoch,
                reported_delay_minutes = EXCLUDED.reported_delay_minutes,
                planned_employee_cnt = EXCLUDED.planned_employee_cnt,
                actual_employee_cnt = EXCLUDED.actual_employee_cnt,
                noshow_cnt = EXCLUDED.noshow_cnt,
                version = moveiq.trip.version + 1
            """;

    private final DatasetService datasets;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final int batchSize;

    public TripIngestionService(
            DatasetService datasets,
            JdbcTemplate jdbc,
            TransactionTemplate tx,
            @Value("${moveiq.ingestion.batch-size:1000}") int batchSize) {
        this.datasets = datasets;
        this.jdbc = jdbc;
        this.tx = tx;
        this.batchSize = batchSize;
    }

    public IngestionSummary ingest() {
        long seen = 0, accepted = 0, rejected = 0;
        List<String> errors = new ArrayList<>();
        int files = 0;
        for (var entry : datasets.resolvedFiles().entrySet()) {
            if (!entry.getKey().startsWith("trips_")) continue;
            files++;
            FileResult result = ingestFile(entry.getValue(), errors);
            seen += result.seen();
            accepted += result.accepted();
            rejected += result.rejected();
        }
        return new IngestionSummary(files, seen, accepted, rejected, List.copyOf(errors));
    }

    private FileResult ingestFile(Path path, List<String> errors) {
        long seen = 0, accepted = 0, rejected = 0;
        List<Object[]> batch = new ArrayList<>(batchSize);
        try (Reader reader = Files.newBufferedReader(path);
             CSVParser parser = new CSVParser(reader, CSVFormat.DEFAULT.builder()
                     .setHeader().setSkipHeaderRecord(true).setIgnoreSurroundingSpaces(true).build())) {
            Map<String, String> headers = normalizeHeaders(parser.getHeaderMap());
            for (CSVRecord row : parser) {
                seen++;
                try {
                    batch.add(toArgs(row, headers));
                    accepted++;
                    if (batch.size() >= batchSize) flush(batch);
                } catch (RuntimeException ex) {
                    rejected++;
                    if (errors.size() < 20) errors.add(path.getFileName() + ":row=" + row.getRecordNumber() + ":" + ex.getMessage());
                }
            }
            flush(batch);
            return new FileResult(seen, accepted, rejected);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to ingest " + path, e);
        }
    }

    private void flush(List<Object[]> batch) {
        if (batch.isEmpty()) return;
        List<Object[]> copy = List.copyOf(batch);
        tx.executeWithoutResult(status -> jdbc.batchUpdate(UPSERT, copy));
        batch.clear();
    }

    private Object[] toArgs(CSVRecord row, Map<String, String> h) {
        String businessUnit = required(value(row, h, "business_unit"), "business_unit");
        Long tripId = requiredLong(value(row, h, "trip_id"), "trip_id");
        LocalDate tripDate = parseDate(required(value(row, h, "trip_date"), "trip_date"));
        return new Object[]{
                businessUnit, tripId, tripDate,
                blankToNull(value(row, h, "office")), blankToNull(value(row, h, "shift_type")),
                blankToNull(value(row, h, "trip_direction")), first(row, h, "vendor_id", "vendor"),
                longValue(value(row, h, "planned_start_epoch")), longValue(value(row, h, "planned_end_epoch")),
                longValue(value(row, h, "actual_start_epoch")), longValue(value(row, h, "actual_end_epoch")),
                intValue(value(row, h, "delay_minutes")), intValue(value(row, h, "planned_employee_cnt")),
                intValue(value(row, h, "actual_employee_cnt")), intValue(value(row, h, "noshow_cnt"))
        };
    }

    private Map<String, String> normalizeHeaders(Map<String, Integer> raw) {
        Map<String, String> result = new LinkedHashMap<>();
        raw.keySet().forEach(name -> result.put(name.trim().toLowerCase(Locale.ROOT), name));
        return result;
    }

    private String value(CSVRecord row, Map<String, String> h, String key) {
        String actual = h.get(key.toLowerCase(Locale.ROOT));
        return actual == null ? "" : row.get(actual);
    }

    private String first(CSVRecord row, Map<String, String> h, String... keys) {
        for (String key : keys) {
            String v = blankToNull(value(row, h, key));
            if (v != null) return v;
        }
        return null;
    }

    private String required(String value, String field) {
        String v = blankToNull(value);
        if (v == null) throw new IllegalArgumentException("missing " + field);
        return v;
    }

    private Long requiredLong(String value, String field) {
        Long parsed = longValue(value);
        if (parsed == null) throw new IllegalArgumentException("invalid " + field + "=" + value);
        return parsed;
    }

    private String blankToNull(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        return value.trim();
    }

    private Long longValue(String value) {
        String v = value == null ? "" : value.trim().replace(",", "");
        if (v.isEmpty()) return null;
        try { return Long.valueOf(v); } catch (NumberFormatException e) { return null; }
    }

    private Integer intValue(String value) {
        Long v = longValue(value);
        if (v == null || v > Integer.MAX_VALUE || v < Integer.MIN_VALUE) return null;
        return v.intValue();
    }

    private LocalDate parseDate(String raw) {
        for (DateTimeFormatter formatter : List.of(
                DateTimeFormatter.ISO_LOCAL_DATE,
                DateTimeFormatter.ofPattern("dd-MM-uuuu"),
                DateTimeFormatter.ofPattern("MM/dd/uuuu"),
                DateTimeFormatter.ofPattern("dd/MM/uuuu"),
                DateTimeFormatter.ofPattern("MM-dd-uuuu"))) {
            try { return LocalDate.parse(raw.trim(), formatter); } catch (DateTimeParseException ignored) { }
        }
        throw new IllegalArgumentException("unsupported trip_date=" + raw);
    }

    private record FileResult(long seen, long accepted, long rejected) {}
    public record IngestionSummary(int files, long rowsSeen, long rowsAccepted, long rowsRejected, List<String> sampleErrors) {}
}
