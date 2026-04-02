package com.modnmetl.modnvote.storage;

import com.modnmetl.modnvote.domain.BallotPreference;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

/**
 * DAO for ordered ballot preference persistence.
 */
public final class BallotPreferenceDao {

    private final DatabaseManager databaseManager;

    public BallotPreferenceDao(DatabaseManager databaseManager) {
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager");
    }

    public void insertPreferences(Connection connection, long ballotId, List<BallotPreference> preferences)
            throws SQLException {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(preferences, "preferences");

        String sql = """
                INSERT INTO ballot_preferences (
                    ballot_id,
                    option_id,
                    rank_position
                ) VALUES (?, ?, ?)
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (BallotPreference preference : preferences) {
                ps.setLong(1, ballotId);
                ps.setLong(2, preference.optionId());
                ps.setInt(3, preference.rankPosition());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }
}