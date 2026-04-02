package com.modnmetl.modnvote.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * DAO for committed ballot persistence.
 *
 * Ballots are the canonical source of truth in ModNVote 2.0.
 * This DAO handles the persisted ballot record itself, while the
 * ordered preferences are stored separately by BallotPreferenceDao.
 */
public final class BallotDao {

    private final DatabaseManager databaseManager;

    public BallotDao(DatabaseManager databaseManager) {
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager");
    }

    public long insertBallot(Connection connection,
                             long pollId,
                             UUID voterUuid,
                             String voterNameSnapshot,
                             String identityKey,
                             String identityType,
                             String ipHash,
                             String floodgateId,
                             Instant submittedAt,
                             String clientPlatform,
                             String ballotHash,
                             String receiptHash,
                             boolean isValid,
                             String invalidReason) throws SQLException {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(voterUuid, "voterUuid");
        Objects.requireNonNull(voterNameSnapshot, "voterNameSnapshot");
        Objects.requireNonNull(identityKey, "identityKey");
        Objects.requireNonNull(identityType, "identityType");
        Objects.requireNonNull(submittedAt, "submittedAt");
        Objects.requireNonNull(clientPlatform, "clientPlatform");
        Objects.requireNonNull(ballotHash, "ballotHash");
        Objects.requireNonNull(receiptHash, "receiptHash");

        String sql = """
                INSERT INTO ballots (
                    poll_id,
                    voter_uuid,
                    voter_name_snapshot,
                    identity_key,
                    identity_type,
                    ip_hash,
                    floodgate_id,
                    submitted_at,
                    client_platform,
                    ballot_hash,
                    receipt_hash,
                    is_valid,
                    invalid_reason
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, pollId);
            ps.setString(2, voterUuid.toString());
            ps.setString(3, voterNameSnapshot);
            ps.setString(4, identityKey);
            ps.setString(5, identityType);

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

            ps.setLong(8, submittedAt.toEpochMilli());
            ps.setString(9, clientPlatform);
            ps.setString(10, ballotHash);
            ps.setString(11, receiptHash);
            ps.setInt(12, isValid ? 1 : 0);

            if (invalidReason != null) {
                ps.setString(13, invalidReason);
            } else {
                ps.setNull(13, java.sql.Types.VARCHAR);
            }

            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }

        throw new SQLException("Failed to insert ballot; no generated key returned.");
    }

    public boolean existsBallotForPollAndVoterUuid(Connection connection, long pollId, UUID voterUuid)
            throws SQLException {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(voterUuid, "voterUuid");

        String sql = """
                SELECT 1
                FROM ballots
                WHERE poll_id = ?
                  AND voter_uuid = ?
                LIMIT 1
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, pollId);
            ps.setString(2, voterUuid.toString());

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
}