package com.modnmetl.modnvote.storage;

import com.modnmetl.modnvote.api.PollStatus;
import com.modnmetl.modnvote.api.PollType;
import com.modnmetl.modnvote.domain.Poll;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * DAO for persisted poll definitions.
 */
public final class PollDao {

    private final DatabaseManager databaseManager;

    public PollDao(DatabaseManager databaseManager) {
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager");
    }

    public long insertPoll(Connection connection, Poll poll, String createdBy, String identityPolicy, String configJson)
            throws SQLException {
        String sql = """
                INSERT INTO polls (
                    slug,
                    title,
                    description,
                    poll_type,
                    status,
                    created_by,
                    created_at,
                    opens_at,
                    closes_at,
                    max_rankings,
                    seat_count,
                    allow_partial_ranking,
                    requires_confirmation,
                    participation_secret,
                    identity_policy,
                    config_json
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, poll.slug());
            ps.setString(2, poll.title());
            ps.setString(3, poll.description());
            ps.setString(4, poll.pollType().name());
            ps.setString(5, poll.status().name());
            ps.setString(6, createdBy);
            ps.setLong(7, Instant.now().toEpochMilli());

            if (poll.opensAt() != null) {
                ps.setLong(8, poll.opensAt().toEpochMilli());
            } else {
                ps.setNull(8, Types.BIGINT);
            }

            if (poll.closesAt() != null) {
                ps.setLong(9, poll.closesAt().toEpochMilli());
            } else {
                ps.setNull(9, Types.BIGINT);
            }

            ps.setInt(10, poll.maxRankings());
            ps.setInt(11, poll.seatCount());
            ps.setInt(12, poll.allowPartialRanking() ? 1 : 0);
            ps.setInt(13, poll.requiresConfirmation() ? 1 : 0);
            ps.setString(14, poll.participationSecret());
            ps.setString(15, identityPolicy);
            ps.setString(16, configJson);

            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }

        throw new SQLException("Failed to insert poll; no generated key returned.");
    }

    public List<Poll> findAllPolls() throws SQLException {
        String sql = """
                SELECT
                    poll_id,
                    slug,
                    title,
                    description,
                    poll_type,
                    status,
                    opens_at,
                    closes_at,
                    max_rankings,
                    seat_count,
                    allow_partial_ranking,
                    requires_confirmation,
                    participation_secret
                FROM polls
                ORDER BY poll_id ASC
                """;

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            List<Poll> out = new ArrayList<>();
            while (rs.next()) {
                out.add(mapRow(rs));
            }
            return out;
        }
    }

    public boolean pollExistsBySlug(String slug) throws SQLException {
        String sql = "SELECT 1 FROM polls WHERE slug = ? LIMIT 1";

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, slug);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public Poll findPollById(long pollId) throws SQLException {
        String sql = """
                SELECT
                    poll_id,
                    slug,
                    title,
                    description,
                    poll_type,
                    status,
                    opens_at,
                    closes_at,
                    max_rankings,
                    seat_count,
                    allow_partial_ranking,
                    requires_confirmation,
                    participation_secret
                FROM polls
                WHERE poll_id = ?
                LIMIT 1
                """;

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, pollId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }

        return null;
    }

    public void updatePollStatus(Connection connection, long pollId, PollStatus status) throws SQLException {
        String sql = "UPDATE polls SET status = ? WHERE poll_id = ?";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setLong(2, pollId);
            ps.executeUpdate();
        }
    }

    private Poll mapRow(ResultSet rs) throws SQLException {
        Long opensAtMillis = getNullableLong(rs, "opens_at");
        Long closesAtMillis = getNullableLong(rs, "closes_at");

        return new Poll(
                rs.getLong("poll_id"),
                rs.getString("slug"),
                rs.getString("title"),
                rs.getString("description"),
                PollType.valueOf(rs.getString("poll_type")),
                PollStatus.valueOf(rs.getString("status")),
                opensAtMillis != null ? Instant.ofEpochMilli(opensAtMillis) : null,
                closesAtMillis != null ? Instant.ofEpochMilli(closesAtMillis) : null,
                rs.getInt("max_rankings"),
                rs.getInt("seat_count"),
                rs.getInt("allow_partial_ranking") == 1,
                rs.getInt("requires_confirmation") == 1,
                rs.getString("participation_secret")
        );
    }

    private Long getNullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}