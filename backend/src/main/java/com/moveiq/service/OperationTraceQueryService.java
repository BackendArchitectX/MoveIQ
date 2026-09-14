package com.moveiq.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moveiq.api.dto.OperationTraceEvent;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class OperationTraceQueryService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public OperationTraceQueryService(
            JdbcTemplate jdbc,
            ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public List<OperationTraceEvent> after(long sequence, int limit) {
        int boundedLimit = Math.max(1, Math.min(limit, 500));

        return jdbc.query(
                """
                SELECT sequence,
                       session_id,
                       situation_id,
                       source_event_id,
                       scope_key,
                       stage,
                       event_type,
                       event_time,
                       recorded_at,
                       summary,
                       evidence
                FROM moveiq.operation_trace
                WHERE sequence > ?
                ORDER BY sequence
                LIMIT ?
                """,
                (rs, rowNum) -> map(rs, rowNum),
                sequence,
                boundedLimit);
    }

    private OperationTraceEvent map(ResultSet rs, int rowNum)
            throws SQLException {

        Map<String, Object> evidence;

        try {
            evidence = objectMapper.readValue(
                    rs.getString("evidence"),
                    new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new SQLException(
                    "Unable to deserialize operation trace evidence",
                    e);
        }

        return new OperationTraceEvent(
                rs.getLong("sequence"),
                rs.getObject("session_id", java.util.UUID.class),
                rs.getObject("situation_id", java.util.UUID.class),
                rs.getString("source_event_id"),
                rs.getString("scope_key"),
                rs.getString("stage"),
                rs.getString("event_type"),
                rs.getTimestamp("event_time").toInstant(),
                rs.getTimestamp("recorded_at").toInstant(),
                rs.getString("summary"),
                evidence);
    }
}