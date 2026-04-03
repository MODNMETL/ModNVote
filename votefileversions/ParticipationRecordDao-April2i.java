package com.modnmetl.modnvote.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Objects;

/**
 * DAO for privacy-safe participation tracking.
 *
 * Participation records prove inclusion and enforce one vote per derived
 * participation token, while never storing vote content.
 */
public final class ParticipationRecordDao {

    private final DatabaseManager databaseManager;

    public ParticipationRecordDao(DatabaseManager databaseManager) {
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager");
    }

    public long insertParticipationRecord(Connection connection,
                                          long pollId,
                                          String participationTokenHash,
                                          Instant submittedAt,
                                          String receiptHash,
                                          String clientPlatform,
                                          String ipHash,
                                          String floodgateId) throws SQLException {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(participationTokenHash, "participationTokenHash");
        Objects.requireNonNull(submittedAt, "submittedAt");
        Objects.requireNonNull(receiptHash, "receiptHash");
        Objects.requireNonNull(clientPlatform, "clientPlatform");

        String sql = """
                INSERT INTO participation_records (
                    poll_id,
                    participation_token_hash,
                    submitted_at,
                    receipt_hash,
                    client_platform,
                    ip_hash,
                    floodgate_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, pollId);
            ps.setString(2, participationTokenHash);
            ps.setLong(3, submittedAt.toEpochMilli());
            ps.setString(4, receiptHash);
            ps.setString(5, clientPlatform);

            if (ipHash != null) {
                ps.setString(6, ipHash);
            } else {
                ps.setNull(6, java.sql.Types.VARCHAR);
            }

            if (floodgateId != null) {
                ps.setString(7, floodgateId);
            } else {
                ps.setNull(7, java.sql.Types.VARCHAR);
            }

            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }

        throw new SQLException("Failed to insert participation record; no generated key returned.");
    }

    public boolean existsParticipationForPollAndTokenHash(Connection connection,
                                                          long pollId,
                                                          String participationTokenHash) throws SQLException {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(participationTokenHash, "participationTokenHash");

        String sql = """
                SELECT 1
                FROM participation_records
                WHERE poll_id = ?
                  AND participation_token_hash = ?
                LIMIT 1
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, pollId);
            ps.setString(2, participationTokenHash);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public String findReceiptHashByPollAndTokenHash(long pollId, String participationTokenHash) throws SQLException {
        Objects.requireNonNull(participationTokenHash, "participationTokenHash");

        String sql = """
                SELECT receipt_hash
                FROM participation_records
                WHERE poll_id = ?
                  AND participation_token_hash = ?
                LIMIT 1
                """;

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, pollId);
            ps.setString(2, participationTokenHash);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("receipt_hash");
                }
            }
        }

        return null;
    }
}