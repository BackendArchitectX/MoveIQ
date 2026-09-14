package com.moveiq.store;

import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ProcessedEventStore {
    private final JdbcTemplate jdbc;

    public ProcessedEventStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean claim(String consumerGroup, String businessUnit, String eventId) {
        int changed = jdbc.update(
                """
                INSERT INTO moveiq.processed_event(consumer_group, business_unit, event_id, processed_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (consumer_group, business_unit, event_id) DO NOTHING
                """,
                consumerGroup,
                businessUnit,
                eventId,
                Instant.now());
        return changed == 1;
    }
}
