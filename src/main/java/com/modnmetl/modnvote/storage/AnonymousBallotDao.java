package com.modnmetl.modnvote.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Objects;

/**
 * DAO for anonymous ballot persistence.
 *
 * Anonymous ballots store vote content without voter identity and are the
 * canonical recount source of truth.
 */
public final class AnonymousBallotDao {

    private final DatabaseManager databaseManager;

    public AnonymousBallotDao(DatabaseManager databaseManager) {
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager");
    }

    public long insertAnonymousBallot(Connection connection,
                                      long pollId,
                                      String ballotHash,
                                      String receiptHash,
                                      Instant submittedAt) throws SQLException {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(ballotHash, "ballotHash");
        Objects.requireNonNull(receiptHash, "receiptHash");
        Objects.requireNonNull(submittedAt, "submittedAt");

        String sql = """
                INSERT INTO anonymous_ballots (
                    poll_id,
                    ballot_hash,
                    receipt_hash,
                    submitted_at
                ) VALUES (?, ?, ?, ?)
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, pollId);
            ps.setString(2, ballotHash);
            ps.setString(3, receiptHash);
            ps.setLong(4, submittedAt.toEpochMilli());
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }

        throw new SQLException("Failed to insert anonymous ballot; no generated key returned.");
    }

    public boolean existsAnonymousBallotForPollAndReceiptHash(long pollId, String receiptHash) throws SQLException {
        Objects.requireNonNull(receiptHash, "receiptHash");

        String sql = """
                SELECT 1
                FROM anonymous_ballots
                WHERE poll_id = ?
                  AND receipt_hash = ?
                LIMIT 1
                """;

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, pollId);
            ps.setString(2, receiptHash);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public java.util.List<StoredAnonymousBallot> findAnonymousBallotsByPollId(long pollId) throws SQLException {
        String sql = """
            SELECT
                anonymous_ballot_id,
                ballot_hash,
                receipt_hash,
                submitted_at
            FROM anonymous_ballots
            WHERE poll_id = ?
            ORDER BY anonymous_ballot_id ASC
            """;

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, pollId);

            try (ResultSet rs = ps.executeQuery()) {
                java.util.List<StoredAnonymousBallot> out = new java.util.ArrayList<>();
                while (rs.next()) {
                    out.add(new StoredAnonymousBallot(
                            rs.getLong("anonymous_ballot_id"),
                            rs.getString("ballot_hash"),
                            rs.getString("receipt_hash"),
                            Instant.ofEpochMilli(rs.getLong("submitted_at"))
                    ));
                }
                return out;
            }
        }
    }

    public java.util.List<String> findReceiptHashesByPollId(long pollId) throws SQLException {
        String sql = """
            SELECT receipt_hash
            FROM anonymous_ballots
            WHERE poll_id = ?
            ORDER BY anonymous_ballot_id ASC
            """;

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, pollId);

            try (ResultSet rs = ps.executeQuery()) {
                java.util.List<String> out = new java.util.ArrayList<>();
                while (rs.next()) {
                    out.add(rs.getString("receipt_hash"));
                }
                return out;
            }
        }
    }

    public record StoredAnonymousBallot(
            long anonymousBallotId,
            String ballotHash,
            String receiptHash,
            Instant submittedAt
    ) {
    }
}