package com.moveiq.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class EvidenceHashService {
    private final JdbcTemplate jdbc;

    public EvidenceHashService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public String compute(UUID situationId) {
        String situation = jdbc.queryForObject(
                """
                SELECT concat_ws('|', correlation_key, status, priority,
                    affected_employees::text, delay_minutes::text, version::text)
                FROM moveiq.situation WHERE id = ?
                """,
                String.class,
                situationId);
        if (situation == null) throw new IllegalArgumentException("Situation not found: " + situationId);

        StringBuilder canonical = new StringBuilder(situation).append('\n');
        jdbc.query(
                """
                SELECT source_event_id, affected_employees, delay_minutes
                FROM moveiq.situation_contribution
                WHERE situation_id = ?
                ORDER BY source_event_id
                """,
                rs -> canonical.append(rs.getString(1)).append('|')
                        .append(rs.getLong(2)).append('|').append(rs.getLong(3)).append('\n'),
                situationId);
        return sha256(canonical.toString());
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
