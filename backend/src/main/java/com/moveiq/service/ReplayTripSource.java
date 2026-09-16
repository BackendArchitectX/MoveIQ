package com.moveiq.service;

import com.moveiq.api.dto.MobilityEvent;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ReplayTripSource {

    private static final String EVENT_EPOCH =
            "COALESCE("
                    + "actual_start_epoch, "
                    + "planned_start_epoch, "
                    + "actual_end_epoch, "
                    + "planned_end_epoch"
                    + ")";

    private final JdbcTemplate jdbc;

    public ReplayTripSource(
            JdbcTemplate jdbc) {

        this.jdbc = jdbc;
    }

    /**
     * Bounds for the complete replay dataset.
     */
    public ReplayBounds bounds() {

        return jdbc.queryForObject(
                "SELECT "
                        + "COUNT(*), "
                        + "MIN(" + EVENT_EPOCH + "), "
                        + "MAX(" + EVENT_EPOCH + ") "
                        + "FROM moveiq.trip "
                        + "WHERE "
                        + EVENT_EPOCH
                        + " IS NOT NULL",
                (rs, rowNum) ->
                        new ReplayBounds(
                                rs.getLong(1),
                                instantOrNull(
                                        rs,
                                        2),
                                instantOrNull(
                                        rs,
                                        3)));
    }

    /**
     * Bounds for a replay that starts at a selected event-time.
     *
     * Only events at or after startAt are included in the run's
     * total and first/last replay bounds.
     */
    public ReplayBounds boundsAtOrAfter(
            Instant startAt) {

        if (startAt == null) {
            return bounds();
        }

        long startEpoch =
                startAt.getEpochSecond();

        return jdbc.queryForObject(
                "SELECT "
                        + "COUNT(*), "
                        + "MIN(" + EVENT_EPOCH + "), "
                        + "MAX(" + EVENT_EPOCH + ") "
                        + "FROM moveiq.trip "
                        + "WHERE "
                        + EVENT_EPOCH
                        + " IS NOT NULL "
                        + "AND "
                        + EVENT_EPOCH
                        + " >= ?",
                (rs, rowNum) ->
                        new ReplayBounds(
                                rs.getLong(1),
                                instantOrNull(
                                        rs,
                                        2),
                                instantOrNull(
                                        rs,
                                        3)),
                startEpoch);
    }

    /**
     * First page for the default replay-from-beginning path.
     */
    public List<ReplayTrip> firstPage(
            int limit) {

        return jdbc.query(
                baseSelect()
                        + " WHERE "
                        + EVENT_EPOCH
                        + " IS NOT NULL"
                        + " ORDER BY "
                        + "event_epoch, "
                        + "business_unit, "
                        + "trip_id "
                        + "LIMIT ?",
                this::map,
                limit);
    }

    /**
     * First page for a replay seek.
     *
     * The timestamp comparison is inclusive so a trip whose
     * event time exactly equals startAt is not skipped.
     */
    public List<ReplayTrip> firstPageAtOrAfter(
            Instant startAt,
            int limit) {

        if (startAt == null) {
            return firstPage(
                    limit);
        }

        long startEpoch =
                startAt.getEpochSecond();

        return jdbc.query(
                baseSelect()
                        + " WHERE "
                        + EVENT_EPOCH
                        + " IS NOT NULL"
                        + " AND "
                        + EVENT_EPOCH
                        + " >= ?"
                        + " ORDER BY "
                        + "event_epoch, "
                        + "business_unit, "
                        + "trip_id "
                        + "LIMIT ?",
                this::map,
                startEpoch,
                limit);
    }

    /**
     * All subsequent pages continue using the existing stable
     * tuple cursor.
     */
    public List<ReplayTrip> nextPage(
            ReplayCursor cursor,
            int limit) {

        return jdbc.query(
                baseSelect()
                        + " WHERE "
                        + EVENT_EPOCH
                        + " IS NOT NULL"
                        + " AND ("
                        + EVENT_EPOCH
                        + ", business_unit, trip_id)"
                        + " > (?, ?, ?)"
                        + " ORDER BY "
                        + "event_epoch, "
                        + "business_unit, "
                        + "trip_id "
                        + "LIMIT ?",
                this::map,
                cursor.eventEpoch(),
                cursor.businessUnit(),
                cursor.tripId(),
                limit);
    }

    private String baseSelect() {

        return "SELECT "
                + "business_unit, "
                + "trip_id, "
                + "office, "
                + "shift_type, "
                + "trip_direction, "
                + "vendor_id, "
                + "reported_delay_minutes, "
                + "planned_employee_cnt, "
                + "actual_employee_cnt, "
                + EVENT_EPOCH
                + " AS event_epoch "
                + "FROM moveiq.trip";
    }

    private ReplayTrip map(
            ResultSet rs,
            int rowNum)
            throws SQLException {

        long eventEpoch =
                rs.getLong(
                        "event_epoch");

        int affected =
                Math.max(
                        0,
                        nullableInt(
                                rs,
                                "actual_employee_cnt",
                                nullableInt(
                                        rs,
                                        "planned_employee_cnt",
                                        0)));

        int delay =
                Math.max(
                        0,
                        nullableInt(
                                rs,
                                "reported_delay_minutes",
                                0));

        String businessUnit =
                rs.getString(
                        "business_unit");

        long tripId =
                rs.getLong(
                        "trip_id");

        Instant occurredAt =
                Instant.ofEpochSecond(
                        eventEpoch);

        MobilityEvent event =
                new MobilityEvent(
                        "replay-"
                                + businessUnit
                                + "-"
                                + tripId
                                + "-"
                                + eventEpoch,
                        businessUnit,
                        tripId,
                        delay > 0
                                ? "LATE_ARRIVAL"
                                : "TRIP_COMPLETED",
                        rs.getString(
                                "office"),
                        rs.getString(
                                "shift_type"),
                        rs.getString(
                                "trip_direction"),
                        rs.getString(
                                "vendor_id"),
                        affected,
                        delay,
                        occurredAt);

        return new ReplayTrip(
                new ReplayCursor(
                        eventEpoch,
                        businessUnit,
                        tripId),
                event);
    }

    private int nullableInt(
            ResultSet rs,
            String column,
            int fallback)
            throws SQLException {

        int value =
                rs.getInt(
                        column);

        return rs.wasNull()
                ? fallback
                : value;
    }

    private Instant instantOrNull(
            ResultSet rs,
            int index)
            throws SQLException {

        long value =
                rs.getLong(
                        index);

        return rs.wasNull()
                ? null
                : Instant.ofEpochSecond(
                value);
    }

    public record ReplayBounds(
            long total,
            Instant firstEventTime,
            Instant lastEventTime) {
    }

    public record ReplayCursor(
            long eventEpoch,
            String businessUnit,
            long tripId) {
    }

    public record ReplayTrip(
            ReplayCursor cursor,
            MobilityEvent event) {
    }
}