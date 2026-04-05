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
 *
 * Privacy model (v2 hardened):
 * - anonymous ballots do not store participation receipts
 * - ballot proof lookup is handled by ballot_proof_hash
 * - exact-ballot verification is supported by ballot_commitment_hash
 */
public final class AnonymousBallotDao {

    private final DatabaseManager databaseManager;

    public AnonymousBallotDao(DatabaseManager databaseManager) {
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager");
    }

    public long insertAnonymousBallot(Connection connection,
                                      long pollId,
                                      String ballotHash,
                                      String ballotProofHash,
                                      String ballotCommitmentHash,
                                      Instant submittedAt) throws SQLException {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(ballotHash, "ballotHash");
        Objects.requireNonNull(ballotProofHash, "ballotProofHash");
        Objects.requireNonNull(ballotCommitmentHash, "ballotCommitmentHash");
        Objects.requireNonNull(submittedAt, "submittedAt");

        String sql = """
                INSERT INTO anonymous_ballots (
                    poll_id,
                    ballot_hash,
                    ballot_proof_hash,
                    ballot_commitment_hash,
                    submitted_at
                ) VALUES (?, ?, ?, ?, ?)
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, pollId);
            ps.setString(2, ballotHash);
            ps.setString(3, ballotProofHash);
            ps.setString(4, ballotCommitmentHash);
            ps.setLong(5, submittedAt.toEpochMilli());
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }

        throw new SQLException("Failed to insert anonymous ballot; no generated key returned.");
    }

    public StoredAnonymousBallot findAnonymousBallotByPollIdAndProofHash(long pollId,
                                                                         String ballotProofHash) throws SQLException {
        Objects.requireNonNull(ballotProofHash, "ballotProofHash");

        String sql = """
                SELECT
                    anonymous_ballot_id,
                    ballot_hash,
                    ballot_proof_hash,
                    ballot_commitment_hash,
                    submitted_at
                FROM anonymous_ballots
                WHERE poll_id = ?
                  AND ballot_proof_hash = ?
                LIMIT 1
                """;

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, pollId);
            ps.setString(2, ballotProofHash);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new StoredAnonymousBallot(
                            rs.getLong("anonymous_ballot_id"),
                            rs.getString("ballot_hash"),
                            rs.getString("ballot_proof_hash"),
                            rs.getString("ballot_commitment_hash"),
                            Instant.ofEpochMilli(rs.getLong("submitted_at"))
                    );
                }
            }
        }

        return null;
    }

    public java.util.List<StoredAnonymousBallot> findAnonymousBallotsByPollId(long pollId) throws SQLException {
        String sql = """
            SELECT
                anonymous_ballot_id,
                ballot_hash,
                ballot_proof_hash,
                ballot_commitment_hash,
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
                            rs.getString("ballot_proof_hash"),
                            rs.getString("ballot_commitment_hash"),
                            Instant.ofEpochMilli(rs.getLong("submitted_at"))
                    ));
                }
                return out;
            }
        }
    }

    public record StoredAnonymousBallot(
            long anonymousBallotId,
            String ballotHash,
            String ballotProofHash,
            String ballotCommitmentHash,
            Instant submittedAt
    ) {
    }
}