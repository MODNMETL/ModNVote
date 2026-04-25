package com.modnmetl.modnvote.storage;

import com.modnmetl.modnvote.domain.PollOption;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * DAO for persisted poll options.
 */
public final class PollOptionDao {

    private final DatabaseManager databaseManager;

    public PollOptionDao(DatabaseManager databaseManager) {
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager");
    }

    public void insertOptions(Connection connection, long pollId, List<PollOption> options) throws SQLException {
        String sql = """
                INSERT INTO poll_options (
                    poll_id,
                    option_key,
                    display_name,
                    description,
                    display_order,
                    icon_type,
                    icon_value,
                    metadata_json
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (PollOption option : options) {
                ps.setLong(1, pollId);
                ps.setString(2, option.key());
                ps.setString(3, option.displayName());
                ps.setString(4, option.description());
                ps.setInt(5, option.displayOrder());
                ps.setString(6, "MATERIAL");
                ps.setString(7, "PAPER");
                ps.setString(8, "{}");
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    public long insertOption(Connection connection, long pollId, PollOption option) throws SQLException {
        String sql = """
                INSERT INTO poll_options (
                    poll_id,
                    option_key,
                    display_name,
                    description,
                    display_order,
                    icon_type,
                    icon_value,
                    metadata_json
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, pollId);
            ps.setString(2, option.key());
            ps.setString(3, option.displayName());
            ps.setString(4, option.description());
            ps.setInt(5, option.displayOrder());
            ps.setString(6, "MATERIAL");
            ps.setString(7, "PAPER");
            ps.setString(8, "{}");
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }

        throw new SQLException("Failed to insert poll option; no generated key returned.");
    }

    public List<PollOption> findOptionsByPollId(long pollId) throws SQLException {
        String sql = """
                SELECT
                    option_id,
                    poll_id,
                    option_key,
                    display_name,
                    description,
                    display_order
                FROM poll_options
                WHERE poll_id = ?
                ORDER BY display_order ASC, option_id ASC
                """;

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, pollId);

            try (ResultSet rs = ps.executeQuery()) {
                List<PollOption> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(mapRow(rs));
                }
                return out;
            }
        }
    }

    public PollOption findOptionById(long optionId) throws SQLException {
        String sql = """
                SELECT
                    option_id,
                    poll_id,
                    option_key,
                    display_name,
                    description,
                    display_order
                FROM poll_options
                WHERE option_id = ?
                LIMIT 1
                """;

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, optionId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }

        return null;
    }

    public void updateOptionDisplayName(Connection connection, long optionId, String displayName) throws SQLException {
        String sql = "UPDATE poll_options SET display_name = ? WHERE option_id = ?";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, displayName);
            ps.setLong(2, optionId);
            ps.executeUpdate();
        }
    }

    public void updateOptionDescription(Connection connection, long optionId, String description) throws SQLException {
        String sql = "UPDATE poll_options SET description = ? WHERE option_id = ?";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, description);
            ps.setLong(2, optionId);
            ps.executeUpdate();
        }
    }

    public void updateOptionDisplayOrder(Connection connection, long optionId, int displayOrder) throws SQLException {
        String sql = "UPDATE poll_options SET display_order = ? WHERE option_id = ?";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, displayOrder);
            ps.setLong(2, optionId);
            ps.executeUpdate();
        }
    }

    public void deleteOption(Connection connection, long optionId) throws SQLException {
        String sql = "DELETE FROM poll_options WHERE option_id = ?";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, optionId);
            ps.executeUpdate();
        }
    }

    private PollOption mapRow(ResultSet rs) throws SQLException {
        return new PollOption(
                rs.getLong("option_id"),
                rs.getLong("poll_id"),
                rs.getString("option_key"),
                rs.getString("display_name"),
                rs.getString("description"),
                rs.getInt("display_order")
        );
    }
}