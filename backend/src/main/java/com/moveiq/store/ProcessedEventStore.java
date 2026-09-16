package com.moveiq.store;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ProcessedEventStore {

    private final JdbcTemplate jdbc;

    public ProcessedEventStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean claim(
            String consumerGroup,
            UUID replaySessionId,
            String businessUnit,
            String eventId) {

        int changed = jdbc.update(
                """
                INSERT INTO moveiq.processed_event(
                    consumer_group,
                    replay_session_id,
                    business_unit,
                    event_id,
                    processed_at
                )
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT DO NOTHING
                """,
                consumerGroup,
                replaySessionId,
                businessUnit,
                eventId,
                Timestamp.from(Instant.now()));

        return changed == 1;
    }

    public boolean claim(
            String consumerGroup,
            String businessUnit,
            String eventId) {

        return claim(
                consumerGroup,
                null,
                businessUnit,
                eventId);
    }
}