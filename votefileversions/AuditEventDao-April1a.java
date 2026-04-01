package com.modnmetl.modnvote.storage;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;

/**
 * DAO for append-only audit event persistence.
 *
 * This is the first structural implementation of the 2.0 audit chain.
 * Each poll event stores:
 * - a per-poll sequence number
 * - a canonical payload
 * - the previous event hash
 * - the current event hash
 *
 * This is intentionally lightweight for the first lifecycle step and will later
 * be extended with richer payloads and checkpoint publication.
 */
public final class AuditEventDao {

    private final DatabaseManager databaseManager;

    public AuditEventDao(DatabaseManager databaseManager) {
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager");
    }

    public void insertPollEvent(Connection connection, long pollId, String eventType, String canonicalPayload)
            throws SQLException {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(canonicalPayload, "canonicalPayload");

        int nextSequence = getNextSequence(connection, pollId);
        String previousHash = getLatestHash(connection, pollId);
        String eventHash = hash(previousHash == null ? "" : previousHash, eventType, canonicalPayload);

        String sql = """
                INSERT INTO audit_events (
                    poll_id,
                    sequence_no,
                    event_type,
                    canonical_payload,
                    prev_hash,
                    event_hash,
                    created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, pollId);
            ps.setInt(2, nextSequence);
            ps.setString(3, eventType);
            ps.setString(4, canonicalPayload);

            if (previousHash != null) {
                ps.setString(5, previousHash);
            } else {
                ps.setNull(5, java.sql.Types.VARCHAR);
            }

            ps.setString(6, eventHash);
            ps.setLong(7, Instant.now().toEpochMilli());
            ps.executeUpdate();
        }
    }

    private int getNextSequence(Connection connection, long pollId) throws SQLException {
        String sql = "SELECT COALESCE(MAX(sequence_no), 0) FROM audit_events WHERE poll_id = ?";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, pollId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) + 1;
                }
            }
        }

        return 1;
    }

    private String getLatestHash(Connection connection, long pollId) throws SQLException {
        String sql = """
                SELECT event_hash
                FROM audit_events
                WHERE poll_id = ?
                ORDER BY sequence_no DESC
                LIMIT 1
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, pollId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                }
            }
        }

        return null;
    }

    private String hash(String previousHash, String eventType, String canonicalPayload) {
        try {
            String input = previousHash + "\n" + eventType + "\n" + canonicalPayload;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to hash audit event", e);
        }
    }
}