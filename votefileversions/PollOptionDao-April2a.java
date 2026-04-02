package com.modnmetl.modnvote.storage;

import com.modnmetl.modnvote.domain.PollOption;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
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
}